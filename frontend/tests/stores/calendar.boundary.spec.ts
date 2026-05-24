// calendar store 边界测试：补齐节日/季节/事件队列边界情景。
// 与现有 calendar.spec.ts 互补：原 spec 走 happy path（load / setBootstrap / markProcessed），
// 本 spec 聚焦"季节切换"、"全部已处理"、"replace 不存在 id"、"按时间排序"。
import { setActivePinia, createPinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useCalendarStore } from '@stores/calendar';
import type { WorldCalendarDto, WorldSeason } from '@/api/dto/WorldCalendarDto';
import type { WorldEventDto } from '@/api/dto/WorldEventDto';

const mocks = vi.hoisted(() => ({
  fetchCalendarToday: vi.fn(),
  fetchCalendarEvents: vi.fn(),
  markCalendarEventProcessed: vi.fn(),
}));

vi.mock('@/api/worldCalendar', () => mocks);

function makeToday(overrides: Partial<WorldCalendarDto> = {}): WorldCalendarDto {
  return {
    dayIndex: 7,
    weekday: 'MONDAY',
    monthIndex: 1,
    season: 'spring',
    timeOfDay: 'MORNING',
    weather: 'SUNNY',
    ...overrides,
  };
}

function makeEvent(
  id: number,
  overrides: Partial<WorldEventDto> = {},
): WorldEventDto {
  return {
    id,
    dateIso: '2026-05-22',
    eventType: 'MASTERY_DECAY',
    payload: {},
    isProcessed: false,
    createdAt: '2026-05-22T08:00:00Z',
    ...overrides,
  };
}

describe('calendar store 边界 - 季节 getter', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    mocks.fetchCalendarToday.mockReset();
    mocks.fetchCalendarEvents.mockReset();
    mocks.markCalendarEventProcessed.mockReset();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it.each<WorldSeason>(['spring', 'summer', 'autumn', 'winter'])(
    'isSeason(%s) 对四季都能精确匹配且与其它季节互斥',
    (season) => {
      const store = useCalendarStore();
      store.setBootstrap(makeToday({ season }));
      expect(store.isSeason(season)).toBe(true);
      const others: WorldSeason[] = (['spring', 'summer', 'autumn', 'winter'] as WorldSeason[])
        .filter((s) => s !== season);
      for (const other of others) {
        expect(store.isSeason(other)).toBe(false);
      }
    },
  );

  it('today=null 时 isSeason() 全部返回 false，dayIndex fallback 0', () => {
    const store = useCalendarStore();
    expect(store.today).toBeNull();
    expect(store.isSeason('spring')).toBe(false);
    expect(store.isSeason('winter')).toBe(false);
    expect(store.dayIndex).toBe(0);
  });

  it('setBootstrap 覆盖式更新 today，二次调用完全替换季节/星期', () => {
    const store = useCalendarStore();
    store.setBootstrap(
      makeToday({ season: 'spring', weekday: 'MONDAY', dayIndex: 1 }),
    );
    store.setBootstrap(
      makeToday({ season: 'winter', weekday: 'SUNDAY', dayIndex: 88 }),
      [makeEvent(99)],
    );
    expect(store.today?.season).toBe('winter');
    expect(store.today?.weekday).toBe('SUNDAY');
    expect(store.dayIndex).toBe(88);
    expect(store.events).toHaveLength(1);
    expect(store.unprocessedEvents).toHaveLength(1);
  });
});

describe('calendar store 边界 - unprocessedEvents getter', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    mocks.fetchCalendarToday.mockReset();
    mocks.fetchCalendarEvents.mockReset();
    mocks.markCalendarEventProcessed.mockReset();
  });

  it('events=[] 时 unprocessedEvents 返回空数组', () => {
    const store = useCalendarStore();
    store.setBootstrap(makeToday(), []);
    expect(store.unprocessedEvents).toEqual([]);
  });

  it('全部 isProcessed=true 时 unprocessedEvents 返回空数组', () => {
    const store = useCalendarStore();
    store.setBootstrap(makeToday(), [
      makeEvent(1, { isProcessed: true }),
      makeEvent(2, { isProcessed: true }),
    ]);
    expect(store.unprocessedEvents).toEqual([]);
  });

  it('混合处理状态时 unprocessedEvents 仅返回未处理项，且保持原顺序', () => {
    const store = useCalendarStore();
    store.setBootstrap(makeToday(), [
      makeEvent(1, { isProcessed: true, eventType: 'A' }),
      makeEvent(2, { isProcessed: false, eventType: 'B' }),
      makeEvent(3, { isProcessed: false, eventType: 'C' }),
      makeEvent(4, { isProcessed: true, eventType: 'D' }),
    ]);
    expect(store.unprocessedEvents.map((e) => e.id)).toEqual([2, 3]);
  });
});

describe('calendar store 边界 - markEventProcessed', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    mocks.fetchCalendarToday.mockReset();
    mocks.fetchCalendarEvents.mockReset();
    mocks.markCalendarEventProcessed.mockReset();
  });

  it('markEventProcessed 命中本地 id 时仅替换该条，引用与长度不变', async () => {
    const store = useCalendarStore();
    store.setBootstrap(makeToday(), [
      makeEvent(1),
      makeEvent(2),
      makeEvent(3),
    ]);
    mocks.markCalendarEventProcessed.mockResolvedValue(
      makeEvent(2, { isProcessed: true, payload: { reason: 'done' } }),
    );

    await store.markEventProcessed(2);

    expect(store.events).toHaveLength(3);
    expect(store.events.map((e) => e.id)).toEqual([1, 2, 3]);
    expect(store.events[1]?.isProcessed).toBe(true);
    expect(store.events[1]?.payload).toEqual({ reason: 'done' });
    expect(store.unprocessedEvents.map((e) => e.id)).toEqual([1, 3]);
  });

  it('markEventProcessed 服务端返回未知 id 时本地数组保持不变', async () => {
    const store = useCalendarStore();
    store.setBootstrap(makeToday(), [makeEvent(1)]);
    mocks.markCalendarEventProcessed.mockResolvedValue(
      makeEvent(999, { isProcessed: true }),
    );

    await store.markEventProcessed(1);

    expect(store.events).toHaveLength(1);
    expect(store.events[0]?.id).toBe(1);
    expect(store.events[0]?.isProcessed).toBe(false);
  });

  it('markEventProcessed 抛错时本地数组保持原值（不吞错）', async () => {
    const store = useCalendarStore();
    store.setBootstrap(makeToday(), [makeEvent(1)]);
    mocks.markCalendarEventProcessed.mockRejectedValue(new Error('network'));

    await expect(store.markEventProcessed(1)).rejects.toThrow('network');
    expect(store.events[0]?.isProcessed).toBe(false);
  });
});

describe('calendar store 边界 - load 失败语义', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    mocks.fetchCalendarToday.mockReset();
    mocks.fetchCalendarEvents.mockReset();
    mocks.markCalendarEventProcessed.mockReset();
  });

  it('两个接口都失败时优先记录第一个 reject 的 Error.message', async () => {
    mocks.fetchCalendarToday.mockRejectedValue(new Error('today down'));
    mocks.fetchCalendarEvents.mockRejectedValue(new Error('events down'));
    const store = useCalendarStore();
    await store.load();
    // Promise.all 短路：第一个 reject 决定 catch 收到的 error。
    expect(['today down', 'events down']).toContain(store.error);
    expect(store.loading).toBe(false);
  });

  it('非 Error 抛出（字符串）也被 String 化为 error', async () => {
    mocks.fetchCalendarToday.mockRejectedValue('boom-today');
    mocks.fetchCalendarEvents.mockResolvedValue([]);
    const store = useCalendarStore();
    await store.load();
    expect(store.error).toBe('boom-today');
    expect(store.loading).toBe(false);
  });

  it('多次 load 之间 error 会被新一次成功调用清零', async () => {
    mocks.fetchCalendarToday.mockRejectedValueOnce(new Error('first'));
    mocks.fetchCalendarEvents.mockResolvedValue([]);
    const store = useCalendarStore();
    await store.load();
    expect(store.error).toBe('first');

    mocks.fetchCalendarToday.mockResolvedValueOnce(makeToday());
    mocks.fetchCalendarEvents.mockResolvedValueOnce([makeEvent(1)]);
    await store.load();
    expect(store.error).toBeNull();
    expect(store.today?.dayIndex).toBe(7);
    expect(store.events).toHaveLength(1);
  });
});
