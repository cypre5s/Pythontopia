// WorldClock 单测：覆盖默认 / 自定义 initial、tick 推进、跨天 / 跨时段 / 跨季节事件、
// syncFromServer、setWeather、advanceDay、订阅顺序与取消。
//
// 测试约定：
// - WorldClock 不依赖 Phaser / Pinia / fetch，纯逻辑模块，无需 mock。
// - 时间断言使用 toBeCloseTo + 5 位精度，规避 (deltaMs/1000)/60 这类浮点累计误差。

import { describe, expect, it } from 'vitest';
import { WorldClock } from '@/game/core/WorldClock';
import type { WorldClockEvent, WorldClockSnapshot } from '@/game/core/WorldClock';

function collect(clock: WorldClock): WorldClockEvent[] {
  const events: WorldClockEvent[] = [];
  clock.on((e) => events.push(e));
  return events;
}

describe('WorldClock', () => {
  describe('initial snapshot', () => {
    it('默认: dayIndex=1, hour=8.0, MORNING, SUNNY, SPRING', () => {
      const clock = new WorldClock();
      expect(clock.snapshot()).toEqual({
        dayIndex: 1,
        hour: 8.0,
        timeOfDay: 'MORNING',
        weather: 'SUNNY',
        season: 'SPRING',
      });
    });

    it('自定义 initial: dayIndex / hour / weather 全部生效，且 timeOfDay / season 由派生算出', () => {
      const clock = new WorldClock({ dayIndex: 30, hour: 22.5, weather: 'RAINY' });
      expect(clock.snapshot()).toEqual({
        dayIndex: 30,
        hour: 22.5,
        timeOfDay: 'NIGHT',
        weather: 'RAINY',
        season: 'SUMMER',
      });
    });

    it('snapshot 返回独立对象，外部修改不会污染时钟内部状态', () => {
      const clock = new WorldClock({ dayIndex: 5, hour: 10, weather: 'CLOUDY' });
      const snap = clock.snapshot();
      snap.dayIndex = 999;
      snap.hour = 0;
      snap.weather = 'SNOWY';
      expect(clock.snapshot()).toEqual({
        dayIndex: 5,
        hour: 10,
        timeOfDay: 'MORNING',
        weather: 'CLOUDY',
        season: 'SPRING',
      });
    });
  });

  describe('static timeOfDayOf', () => {
    it('在 6 个边界 (5/7/11/13/18/21) 各返回正确时段', () => {
      expect(WorldClock.timeOfDayOf(5)).toBe('DAWN');
      expect(WorldClock.timeOfDayOf(7)).toBe('MORNING');
      expect(WorldClock.timeOfDayOf(11)).toBe('NOON');
      expect(WorldClock.timeOfDayOf(13)).toBe('AFTERNOON');
      expect(WorldClock.timeOfDayOf(18)).toBe('EVENING');
      expect(WorldClock.timeOfDayOf(21)).toBe('NIGHT');
    });

    it('小于 5 / 大于等于 21 / 接近 0 都判为 NIGHT', () => {
      expect(WorldClock.timeOfDayOf(0)).toBe('NIGHT');
      expect(WorldClock.timeOfDayOf(4.9999)).toBe('NIGHT');
      expect(WorldClock.timeOfDayOf(23.5)).toBe('NIGHT');
    });
  });

  describe('static seasonOf', () => {
    it('1-28 → SPRING', () => {
      expect(WorldClock.seasonOf(1)).toBe('SPRING');
      expect(WorldClock.seasonOf(14)).toBe('SPRING');
      expect(WorldClock.seasonOf(28)).toBe('SPRING');
    });

    it('29-56 → SUMMER', () => {
      expect(WorldClock.seasonOf(29)).toBe('SUMMER');
      expect(WorldClock.seasonOf(42)).toBe('SUMMER');
      expect(WorldClock.seasonOf(56)).toBe('SUMMER');
    });

    it('57-84 → AUTUMN', () => {
      expect(WorldClock.seasonOf(57)).toBe('AUTUMN');
      expect(WorldClock.seasonOf(70)).toBe('AUTUMN');
      expect(WorldClock.seasonOf(84)).toBe('AUTUMN');
    });

    it('85-112 → WINTER', () => {
      expect(WorldClock.seasonOf(85)).toBe('WINTER');
      expect(WorldClock.seasonOf(100)).toBe('WINTER');
      expect(WorldClock.seasonOf(112)).toBe('WINTER');
    });

    it('113 起跨年循环回 SPRING', () => {
      expect(WorldClock.seasonOf(113)).toBe('SPRING');
      expect(WorldClock.seasonOf(140)).toBe('SPRING');
      expect(WorldClock.seasonOf(141)).toBe('SUMMER');
      expect(WorldClock.seasonOf(225)).toBe('SPRING');
    });
  });

  describe('tick', () => {
    it('默认 rate=1: 60 真实秒 → 1 游戏小时', () => {
      const clock = new WorldClock({ hour: 8.0 });
      clock.tick(60_000);
      expect(clock.snapshot().hour).toBeCloseTo(9.0, 5);
    });

    it('gameMinutesPerRealSecond 自定义生效: rate=60 时, 1 真实秒 → 1 游戏小时', () => {
      const clock = new WorldClock({ hour: 8.0 });
      clock.tick(1_000, 60);
      expect(clock.snapshot().hour).toBeCloseTo(9.0, 5);
    });

    it('hour >= 24 时回卷并 dayIndex+1, 同时触发 day-changed 事件', () => {
      const clock = new WorldClock({ dayIndex: 1, hour: 23.5 });
      const events = collect(clock);
      // 1 真实小时 (3600s) × rate=1 → +60 游戏分钟 = +1 游戏小时
      // 23.5 + 1 = 24.5 → 回卷为 dayIndex=2, hour=0.5
      clock.tick(60_000);

      expect(clock.snapshot().dayIndex).toBe(2);
      expect(clock.snapshot().hour).toBeCloseTo(0.5, 5);

      const dayChanges = events.filter((e) => e.type === 'day-changed');
      expect(dayChanges).toHaveLength(1);
      const first = dayChanges[0];
      expect(first).toBeDefined();
      if (first && first.type === 'day-changed') {
        expect(first.from).toBe(1);
        expect(first.to).toBe(2);
        expect(first.snapshot.dayIndex).toBe(2);
        expect(first.snapshot.hour).toBeCloseTo(0.5, 5);
      }
    });

    it('hour 跨过 timeOfDay 边界时触发 time-of-day-changed', () => {
      // 起始 10.9 (MORNING) → +0.5h → 11.4 (NOON)
      const clock = new WorldClock({ hour: 10.9 });
      const events = collect(clock);
      clock.tick(30_000); // rate=1 → 30s 实时 = 0.5 游戏小时

      const todChanges = events.filter((e) => e.type === 'time-of-day-changed');
      expect(todChanges).toHaveLength(1);
      const first = todChanges[0];
      if (first && first.type === 'time-of-day-changed') {
        expect(first.from).toBe('MORNING');
        expect(first.to).toBe('NOON');
      }
    });

    it('忽略非有限 / 非正 deltaMs，且不触发任何事件', () => {
      const clock = new WorldClock({ hour: 8.0 });
      const events = collect(clock);
      clock.tick(0);
      clock.tick(-100);
      clock.tick(Number.NaN);
      clock.tick(Number.POSITIVE_INFINITY);
      expect(clock.snapshot().hour).toBe(8.0);
      expect(events).toEqual([]);
    });

    it('忽略非正 / 非有限 gameMinutesPerRealSecond，避免反向流时', () => {
      const clock = new WorldClock({ hour: 8.0 });
      const events = collect(clock);
      clock.tick(60_000, 0);
      clock.tick(60_000, -1);
      clock.tick(60_000, Number.NaN);
      expect(clock.snapshot().hour).toBe(8.0);
      expect(events).toEqual([]);
    });
  });

  describe('syncFromServer', () => {
    it('天气真变 → 触发 weather-changed', () => {
      const clock = new WorldClock({ weather: 'SUNNY' });
      const events = collect(clock);
      clock.syncFromServer({ weather: 'RAINY' });

      expect(clock.snapshot().weather).toBe('RAINY');
      expect(events).toHaveLength(1);
      const evt = events[0];
      if (evt && evt.type === 'weather-changed') {
        expect(evt.from).toBe('SUNNY');
        expect(evt.to).toBe('RAINY');
        expect(evt.snapshot.weather).toBe('RAINY');
      } else {
        throw new Error('expected weather-changed event');
      }
    });

    it('patch 与当前状态完全一致时不触发任何事件', () => {
      const clock = new WorldClock({ dayIndex: 5, hour: 12, weather: 'CLOUDY' });
      const events = collect(clock);
      clock.syncFromServer({ dayIndex: 5, hour: 12, weather: 'CLOUDY' });
      expect(events).toEqual([]);
    });

    it('同时变更 dayIndex + weather 时按 day → season → timeOfDay → weather 顺序触发', () => {
      const clock = new WorldClock({ dayIndex: 28, hour: 8, weather: 'SUNNY' }); // SPRING
      const events = collect(clock);
      clock.syncFromServer({ dayIndex: 29, weather: 'RAINY' }); // SUMMER

      const types = events.map((e) => e.type);
      expect(types).toEqual(['day-changed', 'season-changed', 'weather-changed']);
    });

    it('patch 上的 timeOfDay / season 是派生量, 被忽略', () => {
      const clock = new WorldClock({ dayIndex: 1, hour: 8, weather: 'SUNNY' });
      // 故意传入与派生量不符的值，验证 syncFromServer 不会因此修改 dayIndex / hour。
      clock.syncFromServer({ timeOfDay: 'NIGHT', season: 'WINTER' });
      expect(clock.snapshot()).toEqual({
        dayIndex: 1,
        hour: 8,
        timeOfDay: 'MORNING',
        weather: 'SUNNY',
        season: 'SPRING',
      });
    });
  });

  describe('advanceDay', () => {
    it('dayIndex+1, 同季节内只触发 day-changed', () => {
      const clock = new WorldClock({ dayIndex: 5 });
      const events = collect(clock);
      clock.advanceDay();

      expect(clock.snapshot().dayIndex).toBe(6);
      expect(events.map((e) => e.type)).toEqual(['day-changed']);
    });

    it('跨季节时同时触发 day-changed + season-changed (SPRING → SUMMER)', () => {
      const clock = new WorldClock({ dayIndex: 28 });
      const events = collect(clock);
      clock.advanceDay();

      expect(clock.snapshot().dayIndex).toBe(29);
      expect(clock.snapshot().season).toBe('SUMMER');

      const types = events.map((e) => e.type);
      expect(types).toEqual(['day-changed', 'season-changed']);

      const seasonChange = events.find((e) => e.type === 'season-changed');
      if (seasonChange && seasonChange.type === 'season-changed') {
        expect(seasonChange.from).toBe('SPRING');
        expect(seasonChange.to).toBe('SUMMER');
      } else {
        throw new Error('expected season-changed event');
      }
    });
  });

  describe('setWeather', () => {
    it('改变天气时触发 weather-changed', () => {
      const clock = new WorldClock({ weather: 'SUNNY' });
      const events = collect(clock);
      clock.setWeather('SNOWY');

      expect(clock.snapshot().weather).toBe('SNOWY');
      expect(events).toHaveLength(1);
      const evt = events[0];
      if (evt && evt.type === 'weather-changed') {
        expect(evt.from).toBe('SUNNY');
        expect(evt.to).toBe('SNOWY');
      } else {
        throw new Error('expected weather-changed event');
      }
    });

    it('设置相同天气时不触发事件', () => {
      const clock = new WorldClock({ weather: 'SUNNY' });
      const events = collect(clock);
      clock.setWeather('SUNNY');
      expect(events).toEqual([]);
    });
  });

  describe('on (subscribe / unsubscribe)', () => {
    it('on 返回的 unsubscribe 函数会停止后续通知', () => {
      const clock = new WorldClock();
      const events: WorldClockEvent[] = [];
      const off = clock.on((e) => events.push(e));

      clock.setWeather('RAINY');
      expect(events).toHaveLength(1);

      off();
      clock.setWeather('SUNNY');
      expect(events).toHaveLength(1);
    });

    it('多个订阅者按注册顺序依次触发', () => {
      const clock = new WorldClock();
      const order: string[] = [];
      clock.on(() => order.push('a'));
      clock.on(() => order.push('b'));
      clock.on(() => order.push('c'));

      clock.setWeather('CLOUDY');
      expect(order).toEqual(['a', 'b', 'c']);
    });

    it('监听器在回调内取消自己也不会破坏其他监听器的遍历', () => {
      const clock = new WorldClock();
      const order: string[] = [];
      const offA = clock.on(() => {
        order.push('a');
        offA();
      });
      clock.on(() => order.push('b'));

      clock.setWeather('RAINY');
      expect(order).toEqual(['a', 'b']);

      // 再次触发: a 已退订, 只剩 b。
      clock.setWeather('SUNNY');
      expect(order).toEqual(['a', 'b', 'b']);
    });
  });

  describe('event snapshot consistency', () => {
    it('事件 snapshot 字段反映变更后的最终状态', () => {
      const clock = new WorldClock({ dayIndex: 28, hour: 8, weather: 'SUNNY' });
      let captured: WorldClockSnapshot | null = null;
      clock.on((e) => {
        if (e.type === 'season-changed') {
          captured = e.snapshot;
        }
      });
      clock.advanceDay();

      expect(captured).toEqual({
        dayIndex: 29,
        hour: 8,
        timeOfDay: 'MORNING',
        weather: 'SUNNY',
        season: 'SUMMER',
      });
    });
  });
});
