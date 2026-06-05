// cameraTuning：镜头跟随 / 缩放 / 死区 / shake 等所有「魔术数字」集中处。
//
// 零依赖 Phaser：仅导出 readonly 配置对象与纯函数 helper。业务层（WorldScene /
// SceneSwitcher / NpcFramework）读取常量、调用 helper，再翻译成 cameras.main 的
// setLerp / setDeadzone / setZoom / shake 等 Phaser 调用。

/**
 * 玩家移动状态，决定本帧 lerp 因子。
 * - idle: 静止 ≥ 300 ms，回中最平滑
 * - walk: 默认行走
 * - run:  跑步，跟得上 +50% 速度
 */
export type PlayerCameraState = 'idle' | 'walk' | 'run';

/**
 * 镜头调参契约。所有字段 readonly，禁止业务层运行时修改。
 */
export interface CameraTuningConfig {
  /** 桌面 Web 默认 zoom。 */
  readonly zoomBase: number;
  /** 手机横屏 zoom，单 NPC ≥ 80 px 高便于点选。 */
  readonly zoomMobile: number;
  /** 手机竖屏 zoom，扣 80 px HUD 后仍能看清场景。 */
  readonly zoomPortrait: number;
  /** 节日烟花拉远 zoom，视野 20×13 tile 覆盖 town_hub 大半。 */
  readonly zoomFestival: number;
  /** zoom 硬上限；超过 3.0 像素插值毛刺暴露、单屏 < 10 tile 易迷路。 */
  readonly zoomMax: number;

  /** 静止态 lerp，掩盖死区进出抖动。 */
  readonly lerpFactorIdle: number;
  /** walk lerp。 */
  readonly lerpFactorWalk: number;
  /** run lerp，与 +50% 速度成比例提升追赶能力。 */
  readonly lerpFactorRun: number;

  /** 死区半宽（px），玩家中心该范围内镜头不动。 */
  readonly deadZoneHalfWidth: number;
  /** 死区半高，与半宽各向同性，避免上下比左右更敏感。 */
  readonly deadZoneHalfHeight: number;

  /** KC 收获短促 shake 时长（毫秒）。 */
  readonly shakeShortMs: number;
  /** 中等 shake 预留档（中量事件，例：矿洞落石），介于 short 与 long 之间。 */
  readonly shakeMediumMs: number;
  /** 长 shake 预留档（重大事件，例：剧情地震），上限 200 ms 避免眩晕。 */
  readonly shakeLongMs: number;
  /** shake 强度（屏幕高度比例），0.005 ≈ 3 px。 */
  readonly shakeIntensity: number;

  /** 是否启用地图边界 clamp。生产环境恒为 true；关闭仅用于 debug 摄像漫游。 */
  readonly boundsClampEnabled: boolean;
}

/**
 * 默认镜头调参；本对象是唯一事实源，业务层只读。
 *
 * 用 `as const satisfies CameraTuningConfig`：
 * - `as const` 让每个数值取 literal type（防止被 widening 成其它数）
 * - `satisfies` 强制契约一致，又不丢失 literal 信息
 * 两者结合 = 编译期不可变 + 类型安全。
 */
// 注意：仅 `as const satisfies` 在编译期保证不可变，但运行期 JS 对象仍可被改写
// （例如测试里 @ts-expect-error 强赋会真的污染单例），所以再叠一层 Object.freeze
// 防止跨测试污染 / 业务侧偷偷突变。
export const DEFAULT_CAMERA_TUNING = Object.freeze({
  zoomBase: 2.4,
  zoomMobile: 3.2,
  zoomPortrait: 2.8,
  zoomFestival: 2.0,
  zoomMax: 5.0,

  lerpFactorIdle: 0.10,
  lerpFactorWalk: 0.15,
  lerpFactorRun: 0.25,

  deadZoneHalfWidth: 24,
  deadZoneHalfHeight: 24,

  shakeShortMs: 20,
  shakeMediumMs: 80,
  shakeLongMs: 200,
  shakeIntensity: 0.005,

  boundsClampEnabled: true,
} as const) satisfies CameraTuningConfig;

/**
 * 根据玩家状态返回对应 lerp 因子。
 *
 * 业务层每帧调用并把结果丢给 `cameras.main.setLerp(f, f)`；
 * idle↔walk↔run 的「200 ms 内线性过渡」由调用方用 `Phaser.Math.Linear`
 * 自行插值，本函数只返回各档稳定值。
 */
export function getLerpFactorByState(state: PlayerCameraState): number {
  switch (state) {
    case 'idle':
      return DEFAULT_CAMERA_TUNING.lerpFactorIdle;
    case 'walk':
      return DEFAULT_CAMERA_TUNING.lerpFactorWalk;
    case 'run':
      return DEFAULT_CAMERA_TUNING.lerpFactorRun;
  }
}

/**
 * 根据视口宽度与方向选择 zoom 档位。
 *
 * 判定优先级：
 *   isPortrait === true                  → 竖屏档 2.8（无论宽度，覆盖小屏竖手机）
 *   else if viewportWidth < 768          → 手机横屏档 3.2（pointer:coarse 小屏）
 *   else                                 → 桌面档 2.4
 *
 * 768 是 Tailwind / Material 通用的 mobile→tablet 断点；与「pointer: coarse」
 * 媒体查询的真实硬件区间高度吻合，业务层无需再传 isMobile。
 */
export function getZoomForViewport(viewportWidth: number, isPortrait: boolean): number {
  if (isPortrait) {
    return DEFAULT_CAMERA_TUNING.zoomPortrait;
  }
  if (viewportWidth < 768) {
    return DEFAULT_CAMERA_TUNING.zoomMobile;
  }
  return DEFAULT_CAMERA_TUNING.zoomBase;
}

/**
 * 把相机视口左上角的世界坐标 clamp 到地图边界内。
 *
 * 入参：
 *   camX, camY  - 相机视口左上角希望放到的世界坐标（即 Phaser 的 scrollX/scrollY 目标值）
 *   viewW, viewH - 视口在「世界单位」下的宽高（= canvas 像素 / zoom）
 *   mapW, mapH  - 地图世界宽高（tile 数 × RENDER_TILE_SIZE）
 *
 * 行为：
 * - 普通情况：camX 限制在 [0, mapW - viewW]，camY 限制在 [0, mapH - viewH]，
 *   保证视口不出地图边界，呈现「靠墙时玩家偏移到屏幕边」的视觉。
 * - 小图（mapW < viewW 或 mapH < viewH）：镜头中心固定到地图中心，
 *   即 scrollX = (mapW - viewW) / 2（负数），让地图在视口居中、两侧自然留空。
 * - `boundsClampEnabled=false` 时直通透传（仅 debug 漫游）。
 */
export function clampCameraBounds(
  camX: number,
  camY: number,
  viewW: number,
  viewH: number,
  mapW: number,
  mapH: number,
): { x: number; y: number } {
  if (!DEFAULT_CAMERA_TUNING.boundsClampEnabled) {
    return { x: camX, y: camY };
  }
  return {
    x: clampAxis(camX, viewW, mapW),
    y: clampAxis(camY, viewH, mapH),
  };
}

function clampAxis(coord: number, viewSize: number, mapSize: number): number {
  if (mapSize <= viewSize) {
    return (mapSize - viewSize) / 2;
  }
  const maxCoord = mapSize - viewSize;
  if (coord < 0) return 0;
  if (coord > maxCoord) return maxCoord;
  return coord;
}
