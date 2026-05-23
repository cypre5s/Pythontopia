// W4 cameraTuning：镜头跟随 / 缩放 / 死区 / shake 等所有「魔术数字」集中处。
//
// 设计依据：docs/gameplay/r12-deepen/02-camera-follow.md。
// - §2.1 lerp 分档 idle/walk/run = 0.10/0.15/0.25
// - §3.1 死区 ±64 px（边长 128 = 2 tile）
// - §5.1 zoom 分档 桌面 2.0 / 手机横屏 2.5 / 手机竖屏 2.2 / 节日 1.5 / 硬上限 3.0
// - §7.4 KC 收获 shake 短 20 ms / 强度 0.005
// - §4   地图边界 clamp（不暴露 canvas 黑边；小图镜头居中）
//
// 边界与隔离：
// - 本文件严格零依赖 Phaser：仅导出 readonly 配置对象与纯函数 helper。
//   业务层（WorldScene / SceneSwitcher / NpcFramework）按各自时机读取常量、
//   调用 helper，再翻译成 cameras.main.setLerp / setDeadzone / setZoom / shake 等
//   Phaser 调用，由 src/game/scenes 或 src/game/core 承担。
// - 单测只验证「数值合理性 + helper 分支正确」，避免拉起 phaser mock，节约 CI 时间。

/**
 * 玩家移动状态，决定本帧 lerp 因子。
 * - idle: 静止 ≥ 300 ms，回中最平滑
 * - walk: 默认行走，与 W1 基线一致
 * - run:  跑步，跟得上 +50% 速度
 * 详见 §2.1。
 */
export type PlayerCameraState = 'idle' | 'walk' | 'run';

/**
 * 镜头调参契约。所有字段 readonly，禁止业务层运行时修改。
 * 字段命名采用 camelCase，对应章节列在 JSDoc 里。
 */
export interface CameraTuningConfig {
  /** §5.1 桌面 Web 默认 zoom；与 W1 部署值一致。 */
  readonly zoomBase: number;
  /** §5.1 / §9.1 手机横屏 zoom，单 NPC ≥ 80 px 高便于点选。 */
  readonly zoomMobile: number;
  /** §5.1 / §9.1 手机竖屏 zoom，扣 80 px HUD 后仍能看清场景。 */
  readonly zoomPortrait: number;
  /** §5.1 / §7.3 节日烟花拉远 zoom，视野 20×13 tile 覆盖 town_hub 大半。 */
  readonly zoomFestival: number;
  /** §5.1 zoom 硬上限；超过 3.0 像素插值毛刺暴露、单屏 < 10 tile 易迷路。 */
  readonly zoomMax: number;

  /** §2.1 静止态 lerp，掩盖死区进出抖动。 */
  readonly lerpFactorIdle: number;
  /** §2.1 walk lerp（W1 基线）。 */
  readonly lerpFactorWalk: number;
  /** §2.1 run lerp，与 +50% 速度成比例提升追赶能力。 */
  readonly lerpFactorRun: number;

  /** §3.1 死区半宽，玩家中心 ±64 px = 2 tile 内镜头不动。 */
  readonly deadZoneHalfWidth: number;
  /** §3.1 死区半高，与半宽各向同性，避免上下比左右更敏感。 */
  readonly deadZoneHalfHeight: number;

  /** §7.4 KC 收获短促 shake 时长（毫秒）。 */
  readonly shakeShortMs: number;
  /** 中等 shake 预留档（中量事件，例：矿洞落石），介于 short 与 long 之间。 */
  readonly shakeMediumMs: number;
  /** 长 shake 预留档（重大事件，例：剧情地震），上限 200 ms 避免眩晕。 */
  readonly shakeLongMs: number;
  /** §7.4 shake 强度（屏幕高度比例），0.005 ≈ 3 px。 */
  readonly shakeIntensity: number;

  /** §4 是否启用地图边界 clamp。生产环境恒为 true；关闭仅用于 debug 摄像漫游。 */
  readonly boundsClampEnabled: boolean;
}

/**
 * 默认镜头调参。文档 §10 表是这里的「单一事实源」，未来按 Beta 反馈微调
 * 也只改本对象，业务层零改动。
 *
 * 用 `as const satisfies CameraTuningConfig`：
 * - `as const` 让每个数值取 literal type（防止意外被 widening 后赋成其它数）
 * - `satisfies` 强制契约一致，但不丢失 literal 信息
 * 两者结合 = 编译期不可变 + 类型安全。
 */
// 注意：仅 `as const satisfies` 在编译期保证不可变，但运行期 JS 对象仍可被改写
// （例如测试里 @ts-expect-error 强赋会真的污染单例），所以再叠一层 Object.freeze
// 防止跨测试污染 / 业务侧偷偷突变。
export const DEFAULT_CAMERA_TUNING = Object.freeze({
  zoomBase: 2,
  zoomMobile: 2.5,
  zoomPortrait: 2.2,
  zoomFestival: 1.5,
  zoomMax: 3,

  lerpFactorIdle: 0.10,
  lerpFactorWalk: 0.15,
  lerpFactorRun: 0.25,

  deadZoneHalfWidth: 64,
  deadZoneHalfHeight: 64,

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
 * 根据视口宽度与方向选择 zoom 档位。§5.1 / §9.1。
 *
 * 判定优先级：
 *   isPortrait === true                  → 竖屏档 2.2（无论宽度，覆盖小屏竖手机）
 *   else if viewportWidth < 768          → 手机横屏档 2.5（pointer:coarse 小屏）
 *   else                                 → 桌面档 2.0
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
 * 把相机视口左上角的世界坐标 clamp 到地图边界内。§4。
 *
 * 入参：
 *   camX, camY  - 相机视口左上角希望放到的世界坐标（即 Phaser 的 scrollX/scrollY 目标值）
 *   viewW, viewH - 视口在「世界单位」下的宽高（= canvas 像素 / zoom）
 *   mapW, mapH  - 地图世界宽高（tile 数 × RENDER_TILE_SIZE）
 *
 * 行为：
 * - 普通情况：camX 限制在 [0, mapW - viewW]，camY 限制在 [0, mapH - viewH]，
 *   保证视口不出地图边界，对应 §4.2 「靠墙时玩家偏移到屏幕边」的视觉。
 * - 小图（mapW < viewW 或 mapH < viewH，§4.1 第 3 条）：镜头中心固定到地图中心，
 *   即 scrollX = (mapW - viewW) / 2（负数），让地图在视口居中、两侧自然留空。
 *   Beta 五张地图均 ≥ 30×20 tile，桌面 zoom=2 下不触发该分支；预留给 R-13 小副本。
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
