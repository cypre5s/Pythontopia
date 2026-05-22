// 重构原因：daynight 只做相机 tint，jsdom 无法验证 GPU 颜色。本 spec 断言：
//          - applyTimeOfDay 对每个 TimeOfDay 都写入预期 tint 整数
//          - deriveTimeOfDay 把 inGameMinutes 正确分段
//          - 显式 timeOfDay 字符串可以覆盖 inGameMinutes 推导
import { describe, expect, it, vi } from 'vitest';
import { applyTimeOfDay, deriveTimeOfDay, __testing, type TimeOfDay } from '@/game/adapters/daynight';
import type { SceneLike } from '@/game/adapters/sceneTypes';

function createScene(): { scene: SceneLike; setTint: ReturnType<typeof vi.fn> } {
  const setTint = vi.fn();
  const scene = {
    cameras: { main: { setTint } },
  } as unknown as SceneLike;
  return { scene, setTint };
}

describe('applyTimeOfDay', () => {
  const cases: ReadonlyArray<[TimeOfDay, number]> = [
    ['morning', 0xfff4d0],
    ['noon', 0xffffff],
    ['evening', 0xffb070],
    ['night', 0x506080],
  ];

  for (const [time, expected] of cases) {
    it(`${time} 设置 tint = 0x${expected.toString(16)}`, () => {
      const { scene, setTint } = createScene();

      applyTimeOfDay(scene, time);

      expect(setTint).toHaveBeenCalledWith(expected);
    });
  }

  it('内部 TINTS 字典与 4 个时段一一对应', () => {
    const keys = Object.keys(__testing.TINTS).sort();
    expect(keys).toEqual(['evening', 'morning', 'night', 'noon']);
  });
});

describe('deriveTimeOfDay', () => {
  it('inGameMinutes 缺失时默认为 noon', () => {
    expect(deriveTimeOfDay({})).toBe('noon');
    expect(deriveTimeOfDay({ inGameMinutes: null })).toBe('noon');
  });

  it('05:00-10:59 -> morning', () => {
    expect(deriveTimeOfDay({ inGameMinutes: 5 * 60 })).toBe('morning');
    expect(deriveTimeOfDay({ inGameMinutes: 10 * 60 + 59 })).toBe('morning');
  });

  it('11:00-15:59 -> noon', () => {
    expect(deriveTimeOfDay({ inGameMinutes: 11 * 60 })).toBe('noon');
    expect(deriveTimeOfDay({ inGameMinutes: 15 * 60 + 30 })).toBe('noon');
  });

  it('16:00-19:59 -> evening', () => {
    expect(deriveTimeOfDay({ inGameMinutes: 16 * 60 })).toBe('evening');
    expect(deriveTimeOfDay({ inGameMinutes: 19 * 60 + 45 })).toBe('evening');
  });

  it('20:00+ 或凌晨 -> night', () => {
    expect(deriveTimeOfDay({ inGameMinutes: 20 * 60 })).toBe('night');
    expect(deriveTimeOfDay({ inGameMinutes: 2 * 60 })).toBe('night');
    expect(deriveTimeOfDay({ inGameMinutes: 0 })).toBe('night');
  });

  it('负数或大于一天的分钟数按 mod 24h 归一', () => {
    expect(deriveTimeOfDay({ inGameMinutes: -60 })).toBe('night');
    expect(deriveTimeOfDay({ inGameMinutes: 26 * 60 })).toBe('night');
    expect(deriveTimeOfDay({ inGameMinutes: 24 * 60 + 8 * 60 })).toBe('morning');
  });

  it('显式 timeOfDay 字符串覆盖 inGameMinutes', () => {
    expect(deriveTimeOfDay({ inGameMinutes: 12 * 60, timeOfDay: 'night' })).toBe('night');
    expect(deriveTimeOfDay({ inGameMinutes: 2 * 60, timeOfDay: 'morning' })).toBe('morning');
    expect(deriveTimeOfDay({ timeOfDay: 'dusk' })).toBe('evening');
    expect(deriveTimeOfDay({ timeOfDay: 'day' })).toBe('noon');
  });

  it('未知 timeOfDay 字符串退回 inGameMinutes 推导', () => {
    expect(deriveTimeOfDay({ inGameMinutes: 8 * 60, timeOfDay: 'twilight' })).toBe('morning');
  });
});
