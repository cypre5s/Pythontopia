// W4 cameraTuning 单测：验证默认值与 docs/gameplay/r12-deepen/02-camera-follow.md
// §10 表完全一致，三个 helper 在所有分支返回正确数值。
//
// 该测试与 phaser 模块完全解耦，运行期不触达 phaserMock 任何桩；之所以放在
// tests/game/config/ 下，是为了与未来的 WorldScene.camera.spec.ts / WorldScene.deadzone.spec.ts
// 在文件树上聚成一个「镜头测试簇」，方便 W4 之后逐项扩展。
import { describe, expect, it } from 'vitest';

import {
  DEFAULT_CAMERA_TUNING,
  clampCameraBounds,
  getLerpFactorByState,
  getZoomForViewport,
  type CameraTuningConfig,
  type PlayerCameraState,
} from '@/game/config/cameraTuning.config';

describe('DEFAULT_CAMERA_TUNING 默认值', () => {
  it('与 R-12 主线缩放调参逐项一致', () => {
    // R-12（commit e20cc7ad）把镜头拉近、死区收紧；
    // 2026-10-26 修 pixel crawl：zoom 从 2.4/3.2/2.8 统一改为整数 3，
    // 节日 2.0 / 上限 5.0 不变；死区 ±24。
    expect(DEFAULT_CAMERA_TUNING.zoomBase).toBe(3);
    expect(DEFAULT_CAMERA_TUNING.zoomMobile).toBe(3);
    expect(DEFAULT_CAMERA_TUNING.zoomPortrait).toBe(3);
    expect(DEFAULT_CAMERA_TUNING.zoomFestival).toBe(2);
    expect(DEFAULT_CAMERA_TUNING.zoomMax).toBe(5);

    expect(DEFAULT_CAMERA_TUNING.lerpFactorIdle).toBe(0.1);
    expect(DEFAULT_CAMERA_TUNING.lerpFactorWalk).toBe(0.15);
    expect(DEFAULT_CAMERA_TUNING.lerpFactorRun).toBe(0.25);

    expect(DEFAULT_CAMERA_TUNING.deadZoneHalfWidth).toBe(24);
    expect(DEFAULT_CAMERA_TUNING.deadZoneHalfHeight).toBe(24);

    expect(DEFAULT_CAMERA_TUNING.shakeShortMs).toBe(20);
    expect(DEFAULT_CAMERA_TUNING.shakeMediumMs).toBe(80);
    expect(DEFAULT_CAMERA_TUNING.shakeLongMs).toBe(200);
    expect(DEFAULT_CAMERA_TUNING.shakeIntensity).toBe(0.005);

    expect(DEFAULT_CAMERA_TUNING.boundsClampEnabled).toBe(true);
  });

  it('数值合理性：lerp 严格递增 idle < walk < run；run ≤ 1', () => {
    expect(DEFAULT_CAMERA_TUNING.lerpFactorIdle).toBeLessThan(DEFAULT_CAMERA_TUNING.lerpFactorWalk);
    expect(DEFAULT_CAMERA_TUNING.lerpFactorWalk).toBeLessThan(DEFAULT_CAMERA_TUNING.lerpFactorRun);
    expect(DEFAULT_CAMERA_TUNING.lerpFactorIdle).toBeGreaterThan(0);
    expect(DEFAULT_CAMERA_TUNING.lerpFactorRun).toBeLessThanOrEqual(1);
  });

  it('数值合理性：zoom 全部为正整数倍，且 zoomFestival ≤ 各档 ≤ zoomMax', () => {
    // 整数倍是硬约束：非整数 zoom 会让 16px 瓦片像素宽窄不匀（pixel crawl）。
    [
      DEFAULT_CAMERA_TUNING.zoomBase,
      DEFAULT_CAMERA_TUNING.zoomMobile,
      DEFAULT_CAMERA_TUNING.zoomPortrait,
      DEFAULT_CAMERA_TUNING.zoomFestival,
      DEFAULT_CAMERA_TUNING.zoomMax,
    ].forEach((zoom) => {
      expect(zoom).toBeGreaterThan(0);
      expect(Number.isInteger(zoom)).toBe(true);
    });
    expect(DEFAULT_CAMERA_TUNING.zoomFestival).toBeLessThan(DEFAULT_CAMERA_TUNING.zoomBase);
    expect(DEFAULT_CAMERA_TUNING.zoomBase).toBeLessThanOrEqual(DEFAULT_CAMERA_TUNING.zoomPortrait);
    expect(DEFAULT_CAMERA_TUNING.zoomPortrait).toBeLessThanOrEqual(DEFAULT_CAMERA_TUNING.zoomMobile);
    expect(DEFAULT_CAMERA_TUNING.zoomMobile).toBeLessThanOrEqual(DEFAULT_CAMERA_TUNING.zoomMax);
  });

  it('数值合理性：shake 三档严格递增；强度在 (0, 0.05] 安全区间', () => {
    expect(DEFAULT_CAMERA_TUNING.shakeShortMs).toBeLessThan(DEFAULT_CAMERA_TUNING.shakeMediumMs);
    expect(DEFAULT_CAMERA_TUNING.shakeMediumMs).toBeLessThan(DEFAULT_CAMERA_TUNING.shakeLongMs);
    expect(DEFAULT_CAMERA_TUNING.shakeLongMs).toBeLessThanOrEqual(200);
    expect(DEFAULT_CAMERA_TUNING.shakeIntensity).toBeGreaterThan(0);
    expect(DEFAULT_CAMERA_TUNING.shakeIntensity).toBeLessThanOrEqual(0.05);
  });

  it('运行期保护：Object.freeze + readonly 双层守门，赋值在 strict mode 下抛错且值不变', () => {
    // 编译期：@ts-expect-error 守门，任何字段被允许写入都会让 typecheck 立即红。
    // 运行期：Object.freeze 在 strict mode（vitest 默认 ESM 严格）下赋值直接抛 TypeError，
    //         即使忽略错误也读不出突变；这层防御保证测试之间不会因 ts-expect-error
    //         强赋而污染单例（之前实测污染了 zoomBase / boundsClampEnabled）。
    expect(() => {
      // @ts-expect-error readonly 字段禁止赋值
      DEFAULT_CAMERA_TUNING.zoomBase = 99;
    }).toThrow(TypeError);
    expect(() => {
      // @ts-expect-error readonly 字段禁止赋值
      DEFAULT_CAMERA_TUNING.boundsClampEnabled = false;
    }).toThrow(TypeError);

    // 运行期仍是原值（freeze 让赋值无效或抛错）。
    expect(DEFAULT_CAMERA_TUNING.zoomBase).toBe(3);
    expect(DEFAULT_CAMERA_TUNING.boundsClampEnabled).toBe(true);
    expect(Object.isFrozen(DEFAULT_CAMERA_TUNING)).toBe(true);
  });

  it('接口契约：DEFAULT_CAMERA_TUNING 满足 CameraTuningConfig 全部字段', () => {
    // satisfies 已经在源文件保证，这里再补一次运行期 sanity check：
    // 任何字段缺失或类型偏移都会让该赋值红。
    const cfg: CameraTuningConfig = DEFAULT_CAMERA_TUNING;
    expect(Object.keys(cfg).sort()).toEqual(
      [
        'boundsClampEnabled',
        'deadZoneHalfHeight',
        'deadZoneHalfWidth',
        'lerpFactorIdle',
        'lerpFactorRun',
        'lerpFactorWalk',
        'shakeIntensity',
        'shakeLongMs',
        'shakeMediumMs',
        'shakeShortMs',
        'zoomBase',
        'zoomFestival',
        'zoomMax',
        'zoomMobile',
        'zoomPortrait',
      ].sort(),
    );
  });
});

describe('getLerpFactorByState', () => {
  it('idle / walk / run 三档分别返回 0.10 / 0.15 / 0.25', () => {
    expect(getLerpFactorByState('idle')).toBe(0.1);
    expect(getLerpFactorByState('walk')).toBe(0.15);
    expect(getLerpFactorByState('run')).toBe(0.25);
  });

  it('返回值等于 DEFAULT_CAMERA_TUNING 对应字段（单一事实源）', () => {
    const states: ReadonlyArray<PlayerCameraState> = ['idle', 'walk', 'run'] as const;
    const expected: Record<PlayerCameraState, number> = {
      idle: DEFAULT_CAMERA_TUNING.lerpFactorIdle,
      walk: DEFAULT_CAMERA_TUNING.lerpFactorWalk,
      run: DEFAULT_CAMERA_TUNING.lerpFactorRun,
    };
    for (const s of states) {
      expect(getLerpFactorByState(s)).toBe(expected[s]);
    }
  });
});

describe('getZoomForViewport', () => {
  it('桌面（≥768，横屏）返回 zoomBase = 3', () => {
    expect(getZoomForViewport(1920, false)).toBe(3);
    expect(getZoomForViewport(1024, false)).toBe(3);
    expect(getZoomForViewport(768, false)).toBe(3);
  });

  it('手机横屏（<768，横屏）返回 zoomMobile = 3', () => {
    expect(getZoomForViewport(767, false)).toBe(3);
    expect(getZoomForViewport(640, false)).toBe(3);
    expect(getZoomForViewport(360, false)).toBe(3);
  });

  it('竖屏（任意宽度）返回 zoomPortrait = 3', () => {
    expect(getZoomForViewport(360, true)).toBe(3);
    expect(getZoomForViewport(720, true)).toBe(3);
    expect(getZoomForViewport(1080, true)).toBe(3);
    expect(getZoomForViewport(1920, true)).toBe(3);
  });

  it('断点边界：768 走桌面、767 走手机', () => {
    expect(getZoomForViewport(768, false)).toBe(DEFAULT_CAMERA_TUNING.zoomBase);
    expect(getZoomForViewport(767, false)).toBe(DEFAULT_CAMERA_TUNING.zoomMobile);
  });
});

describe('clampCameraBounds', () => {
  const VIEW_W = 480; // zoom=2 下桌面视口的世界宽（960 / 2）
  const VIEW_H = 320; // zoom=2 下桌面视口的世界高（640 / 2）
  const MAP_W = 1600; // 50×32 大地图
  const MAP_H = 1280; // 40×32 大地图

  it('居中位置不变（地图中心镜头）', () => {
    const cx = (MAP_W - VIEW_W) / 2; // 560
    const cy = (MAP_H - VIEW_H) / 2; // 480
    expect(clampCameraBounds(cx, cy, VIEW_W, VIEW_H, MAP_W, MAP_H)).toEqual({
      x: cx,
      y: cy,
    });
  });

  it('左上越界 clamp 到 (0, 0)', () => {
    expect(clampCameraBounds(-100, -50, VIEW_W, VIEW_H, MAP_W, MAP_H)).toEqual({
      x: 0,
      y: 0,
    });
    expect(clampCameraBounds(0, 0, VIEW_W, VIEW_H, MAP_W, MAP_H)).toEqual({
      x: 0,
      y: 0,
    });
  });

  it('右下越界 clamp 到 (mapW - viewW, mapH - viewH)', () => {
    const maxX = MAP_W - VIEW_W; // 1120
    const maxY = MAP_H - VIEW_H; // 960
    expect(clampCameraBounds(9999, 9999, VIEW_W, VIEW_H, MAP_W, MAP_H)).toEqual({
      x: maxX,
      y: maxY,
    });
    expect(clampCameraBounds(maxX, maxY, VIEW_W, VIEW_H, MAP_W, MAP_H)).toEqual({
      x: maxX,
      y: maxY,
    });
  });

  it('X 与 Y 独立 clamp（一个越界、另一个正常）', () => {
    expect(clampCameraBounds(-20, 300, VIEW_W, VIEW_H, MAP_W, MAP_H)).toEqual({
      x: 0,
      y: 300,
    });
    expect(clampCameraBounds(700, 9999, VIEW_W, VIEW_H, MAP_W, MAP_H)).toEqual({
      x: 700,
      y: MAP_H - VIEW_H,
    });
  });

  it('小图（mapW < viewW）镜头中心固定到地图中心，scrollX 为负', () => {
    const smallMapW = 320; // 小于视口 480
    const smallMapH = 200; // 小于视口 320
    const result = clampCameraBounds(0, 0, VIEW_W, VIEW_H, smallMapW, smallMapH);
    expect(result.x).toBe((smallMapW - VIEW_W) / 2); // -80
    expect(result.y).toBe((smallMapH - VIEW_H) / 2); // -60
    expect(result.x).toBeLessThan(0);
    expect(result.y).toBeLessThan(0);
  });

  it('边界等同视口尺寸（mapW === viewW）走小图分支，返回 0', () => {
    const result = clampCameraBounds(123, 456, VIEW_W, VIEW_H, VIEW_W, VIEW_H);
    expect(result).toEqual({ x: 0, y: 0 });
  });

  it('返回新对象，不修改入参语义（纯函数）', () => {
    const r1 = clampCameraBounds(100, 200, VIEW_W, VIEW_H, MAP_W, MAP_H);
    const r2 = clampCameraBounds(100, 200, VIEW_W, VIEW_H, MAP_W, MAP_H);
    expect(r1).toEqual(r2);
    expect(r1).not.toBe(r2); // 引用不等 = 每次新建对象
  });
});
