// 重构原因：daynight 只做屏幕叠色，jsdom 无法验证 GPU 颜色。本 spec 断言：
//          - applyTimeOfDay 为每个时段创建正确的叠色矩形（起色 + alpha），noon 不创建
//          - 叠色层是屏幕固定（scrollFactor 0）且深度低于 mood 氛围层
//          - deriveTimeOfDay 把 inGameMinutes 正确分段，显式 timeOfDay 可覆盖推导
//
// 历史坑：旧实现调的是 `camera.setTint()`，而 Phaser 3.87 的 Camera 没有这个方法，
//        `?.()` 让它静默变成空操作——线上昼夜色调从来没生效过。所以这里改断叠色矩形，
//        不再断「setTint 被调用」这种「调了就算过」的假证据。
import { describe, expect, it, vi } from 'vitest';
import { applyTimeOfDay, deriveTimeOfDay, TIME_OVERLAY_DEPTH, __testing, type TimeOfDay } from '@/game/adapters/daynight';
import type { SceneLike } from '@/game/adapters/sceneTypes';

interface RectRec {
  x: number;
  y: number;
  width: number;
  height: number;
  fill: number;
  alpha: number;
  scrollFactor: number | null;
  depth: number | null;
  destroyed: boolean;
}

function createScene(): { scene: SceneLike; rects: RectRec[] } {
  const rects: RectRec[] = [];
  const scene = {
    cameras: { main: { width: 960, height: 640 } },
    add: {
      rectangle: (x: number, y: number, w: number, h: number, fill: number, alpha: number) => {
        const rec: RectRec = {
          x, y, width: w, height: h, fill, alpha,
          scrollFactor: null, depth: null, destroyed: false,
        };
        rects.push(rec);
        const rect = {
          setOrigin: () => rect,
          setScrollFactor: (f: number) => { rec.scrollFactor = f; return rect; },
          setDepth: (d: number) => { rec.depth = d; return rect; },
          setAlpha: (a: number) => { rec.alpha = a; return rect; },
          setSize: (nw: number, nh: number) => { rec.width = nw; rec.height = nh; return rect; },
          setPosition: (nx: number, ny: number) => { rec.x = nx; rec.y = ny; return rect; },
          destroy: () => { rec.destroyed = true; },
        };
        return rect;
      },
    },
  } as unknown as SceneLike;
  return { scene, rects };
}

describe('applyTimeOfDay', () => {
  const cases: ReadonlyArray<[TimeOfDay, number, number]> = [
    ['morning', 0xffe9b8, 0.06],
    ['evening', 0xff8a3c, 0.18],
    ['night', 0x0b1430, 0.5],
  ];

  for (const [time, color, alpha] of cases) {
    it(`${time} 叠一层 0x${color.toString(16)} / alpha ${alpha} 的屏幕固定色玻璃`, () => {
      const { scene, rects } = createScene();

      applyTimeOfDay(scene, time);

      expect(rects).toHaveLength(1);
      const rect = rects[0]!;
      expect(rect.fill).toBe(color);
      // 创建时 alpha 传 0 再 setAlpha：避免 Phaser 先渲染一帧全不透明色块。
      expect(rect.alpha).toBeCloseTo(alpha, 5);
      expect(rect.scrollFactor).toBe(0);
      expect(rect.depth).toBe(TIME_OVERLAY_DEPTH);
      // 叠色层必须低于 mood 氛围层（150），这样「雨天 + 夜晚」时雨的氛围在最外层。
      expect(TIME_OVERLAY_DEPTH).toBeLessThan(150);
    });
  }

  it('noon 不创建任何叠色对象（保持画面原色）', () => {
    const { scene, rects } = createScene();
    const handle = applyTimeOfDay(scene, 'noon');
    expect(rects).toHaveLength(0);
    handle.destroy(); // no-op 句柄不能抛
  });

  it('内部叠色表与 4 个时段一一对应', () => {
    expect(Object.keys(__testing.TIME_OVERLAY).sort()).toEqual(['evening', 'morning', 'night', 'noon']);
  });

  it('destroy 会销毁叠色矩形（场景 restart 不残留）', () => {
    const { scene, rects } = createScene();
    const handle = applyTimeOfDay(scene, 'night');
    handle.destroy();
    expect(rects[0]!.destroyed).toBe(true);
    handle.destroy(); // 幂等
  });

  it('真实 Phaser 场景缺少 add.rectangle（测试桩）时不抛错', () => {
    const scene = { cameras: { main: {} } } as unknown as SceneLike;
    expect(() => applyTimeOfDay(scene, 'night')).not.toThrow();
  });
});

describe('deriveTimeOfDay', () => {
  const cases: ReadonlyArray<[number, TimeOfDay]> = [
    [5 * 60, 'morning'],
    [10 * 60 + 59, 'morning'],
    [11 * 60, 'noon'],
    [15 * 60 + 59, 'noon'],
    [16 * 60, 'evening'],
    [19 * 60 + 59, 'evening'],
    [20 * 60, 'night'],
    [2 * 60, 'night'],
  ];
  for (const [minutes, expected] of cases) {
    it(`${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')} → ${expected}`, () => {
      expect(deriveTimeOfDay({ inGameMinutes: minutes })).toBe(expected);
    });
  }

  it('缺失 inGameMinutes 时按正午处理', () => {
    expect(deriveTimeOfDay({})).toBe('noon');
    expect(deriveTimeOfDay({ inGameMinutes: null })).toBe('noon');
  });

  it('显式 timeOfDay 字符串覆盖 inGameMinutes 推导', () => {
    expect(deriveTimeOfDay({ inGameMinutes: 2 * 60, timeOfDay: 'noon' })).toBe('noon');
    expect(deriveTimeOfDay({ inGameMinutes: 12 * 60, timeOfDay: 'night' })).toBe('night');
    expect(deriveTimeOfDay({ timeOfDay: 'dusk' })).toBe('evening');
    expect(deriveTimeOfDay({ timeOfDay: 'day' })).toBe('noon');
  });

  it('未知 timeOfDay 字符串回落到 inGameMinutes', () => {
    expect(deriveTimeOfDay({ inGameMinutes: 21 * 60, timeOfDay: 'whatever' })).toBe('night');
  });

  it('负分钟数与跨日小时也能归一化', () => {
    expect(deriveTimeOfDay({ inGameMinutes: -60 })).toBe('night');
    expect(deriveTimeOfDay({ inGameMinutes: 24 * 60 + 12 * 60 })).toBe('noon');
  });
});
