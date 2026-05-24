// R-13 Phase 8 大爆炸测试 · DTO 字段契约 + Store 边界（目标 50 用例）。
//
// 覆盖：
//   - 所有 R-13 修改/新建 DTO 的字段（编译期 type assertion + 运行期形状）
//   - Direction 4/8 方向枚举
//   - Weather union（RAIN/RAINY/SUNNY/...）
//   - UserRole（含 ADMIN）
//   - NpcDialogEffect 新增 key/value 字段
//   - GameBootstrap pendingEvents/events 双别名
//   - WorldSleep newEvents/events 双别名
//   - Shop ShopListingDto/ShopBuyRequest/ShopBuyResponseDto
//   - Festival/FastTravel 静态数据完整性
//   - hud/calendar/world store 各 action 副作用

import { setActivePinia, createPinia } from 'pinia';
import { beforeEach, describe, expect, it } from 'vitest';
import type { Direction, CardinalDirection } from '@/api/dto/Direction';
import type { WorldWeather } from '@/api/dto/WorldSessionDto';
import type { UserRole } from '@/api/dto/UserDto';
import type { NpcDialogEffectPayload } from '@/api/dto/NpcDialogStateDto';
import type {
  GameBootstrapResponseDto,
  WorldSleepResponseDto,
} from '@/api/dto/GameBootstrapResponseDto';
import type {
  ShopListingDto,
  ShopBuyRequest,
  ShopBuyResponseDto,
} from '@/api/dto/ShopDto';
import { FESTIVALS, findCurrentFestival, findUpcomingFestival } from '@/data/festivals';
import { FAST_TRAVEL_ROUTES } from '@/data/fastTravel';
import { useHudStore, bgmLabel } from '@stores/hud';
import { useCalendarStore } from '@stores/calendar';
import type { WorldCalendarDto } from '@/api/dto/WorldCalendarDto';
import type { WorldEventDto } from '@/api/dto/WorldEventDto';

describe('contracts.integration: DTO 字段 + store 边界', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  // ---------- Direction（8 方向） ----------
  it.each<[Direction, true]>([
    ['UP', true],
    ['DOWN', true],
    ['LEFT', true],
    ['RIGHT', true],
    ['UP_LEFT', true],
    ['UP_RIGHT', true],
    ['DOWN_LEFT', true],
    ['DOWN_RIGHT', true],
  ])('Direction "%s" 是合法 union 成员', (dir) => {
    const x: Direction = dir;
    expect(x).toBe(dir);
  });

  it('CardinalDirection 是 Direction 的子集', () => {
    const c: CardinalDirection = 'UP';
    const d: Direction = c;
    expect(d).toBe('UP');
  });

  // ---------- Weather union ----------
  it.each<WorldWeather>([
    'SUNNY',
    'CLOUDY',
    'RAIN',
    'WIND',
    'RAINY',
    'WINDY',
    'SNOWY',
    'STORMY',
  ])('Weather "%s" 是合法 union 成员', (w) => {
    const x: WorldWeather = w;
    expect(typeof x).toBe('string');
  });

  // ---------- UserRole（含 ADMIN） ----------
  it.each<UserRole>(['STUDENT', 'TEACHER', 'ADMIN'])('UserRole "%s" 合法', (r) => {
    const x: UserRole = r;
    expect(['STUDENT', 'TEACHER', 'ADMIN']).toContain(x);
  });

  // ---------- NpcDialogEffect 新增 key/value ----------
  it('NpcDialogEffectPayload SET_FLAG 用 key/value 字段（与后端对齐）', () => {
    const eff: NpcDialogEffectPayload = {
      type: 'SET_FLAG',
      key: 'greeted_first_time',
      value: '1',
    };
    expect(eff.key).toBe('greeted_first_time');
    expect(eff.value).toBe('1');
  });

  it('NpcDialogEffectPayload COMPLETE_NODE 用 key 字段', () => {
    const eff: NpcDialogEffectPayload = {
      type: 'COMPLETE_NODE',
      key: 'node_001',
    };
    expect(eff.key).toBe('node_001');
  });

  it('NpcDialogEffectPayload FRIENDSHIP_DELTA 用 delta 字段', () => {
    const eff: NpcDialogEffectPayload = {
      type: 'FRIENDSHIP_DELTA',
      delta: 10,
    };
    expect(eff.delta).toBe(10);
  });

  it('NpcDialogEffectPayload 历史字段 flagKey/itemKey/questKey 仍可选（向后兼容）', () => {
    const eff: NpcDialogEffectPayload = {
      type: 'CUSTOM',
      flagKey: 'foo',
      itemKey: 'bar',
      questKey: 'baz',
    };
    expect(eff.flagKey).toBe('foo');
  });

  // ---------- GameBootstrap 字段别名 ----------
  it('GameBootstrapResponseDto 同时接受 events 与 pendingEvents', () => {
    const e: WorldEventDto[] = [];
    const a: Partial<GameBootstrapResponseDto> = { events: e };
    const b: Partial<GameBootstrapResponseDto> = { pendingEvents: e };
    expect(a.events).toEqual([]);
    expect(b.pendingEvents).toEqual([]);
  });

  it('GameBootstrapResponseDto calendar 是可选字段（后端真实没有）', () => {
    const r: Partial<GameBootstrapResponseDto> = {};
    expect(r.calendar).toBeUndefined();
  });

  // ---------- WorldSleep 字段别名 ----------
  it('WorldSleepResponseDto 同时接受 events 与 newEvents', () => {
    const e: WorldEventDto[] = [];
    const a: Partial<WorldSleepResponseDto> = { events: e };
    const b: Partial<WorldSleepResponseDto> = { newEvents: e };
    expect(a.events).toEqual([]);
    expect(b.newEvents).toEqual([]);
  });

  // ---------- Shop DTO 字段 ----------
  it('ShopListingDto 必须字段', () => {
    const l: ShopListingDto = {
      shopKey: 'merchant_port',
      itemKey: 'python_book',
      priceCoins: 100,
      priceGems: 0,
      stock: 5,
      availableDays: null,
    };
    expect(l.shopKey).toBe('merchant_port');
    expect(l.stock).toBe(5);
  });

  it('ShopListingDto availableDays 可为 null 或 number[]', () => {
    const a: ShopListingDto = {
      shopKey: 'x',
      itemKey: 'y',
      priceCoins: 0,
      priceGems: 0,
      stock: null,
      availableDays: null,
    };
    const b: ShopListingDto = { ...a, availableDays: [1, 2, 3] };
    expect(a.availableDays).toBeNull();
    expect(b.availableDays).toEqual([1, 2, 3]);
  });

  it('ShopBuyRequest 字段', () => {
    const r: ShopBuyRequest = { itemKey: 'python_book', qty: 1 };
    expect(r.qty).toBe(1);
  });

  it('ShopBuyResponseDto 字段', () => {
    const r: ShopBuyResponseDto = {
      itemKey: 'python_book',
      qty: 1,
      totalCoins: 100,
      totalGems: 0,
      remainingStock: 4,
    };
    expect(r.remainingStock).toBe(4);
  });

  // ---------- Festival 静态数据 ----------
  it('FESTIVALS 长度 ≥ 11（V118 seed 对齐）', () => {
    expect(FESTIVALS.length).toBeGreaterThanOrEqual(11);
  });

  it.each([1, 14, 28, 35, 56, 60, 70, 84, 90, 100, 112])(
    'day=%i 命中 FESTIVALS',
    (d) => {
      expect(findCurrentFestival(d)).not.toBeNull();
    },
  );

  it.each([0, 5, 13, 27, 50, 200])(
    'day=%i 非节日命中 upcoming 或 null',
    (d) => {
      const upcoming = findUpcomingFestival(d);
      if (d < 112) {
        expect(upcoming).not.toBeNull();
      }
    },
  );

  // ---------- FastTravel 静态数据 ----------
  it('FAST_TRAVEL_ROUTES 长度 = 5（5 个地图）', () => {
    expect(FAST_TRAVEL_ROUTES.length).toBe(5);
  });

  it.each(['town_hub', 'farm_north', 'mine_south', 'campus_east', 'port_west'])(
    'FAST_TRAVEL_ROUTES 含 mapKey=%s',
    (mapKey) => {
      const has = FAST_TRAVEL_ROUTES.some((r) => r.toMap === mapKey);
      expect(has).toBe(true);
    },
  );

  // ---------- HUD store 边界 ----------
  it('hud.settingsOpen 默认 false + toggle 双向', () => {
    const hud = useHudStore();
    expect(hud.settingsOpen).toBe(false);
    hud.toggleSettings();
    expect(hud.settingsOpen).toBe(true);
    hud.toggleSettings();
    expect(hud.settingsOpen).toBe(false);
  });

  it('hud.openSettings 幂等', () => {
    const hud = useHudStore();
    hud.openSettings();
    hud.openSettings();
    expect(hud.settingsOpen).toBe(true);
  });

  it('hud.closeSettings 幂等', () => {
    const hud = useHudStore();
    hud.closeSettings();
    hud.closeSettings();
    expect(hud.settingsOpen).toBe(false);
  });

  it('hud.reset 清空 settingsOpen', () => {
    const hud = useHudStore();
    hud.openSettings();
    hud.reset();
    expect(hud.settingsOpen).toBe(false);
  });

  it.each([
    ['bgm_town_day', '小镇·白日'],
    ['bgm_town_eve', '小镇·黄昏'],
    ['bgm_bug_mine', 'Bug 矿洞'],
    ['bgm_quest', '校园钟声'],
    [null, '静默'],
    ['unknown_bgm', 'unknown_bgm'],
  ])('bgmLabel("%s") = %s', (key, expected) => {
    expect(bgmLabel(key)).toBe(expected);
  });

  // ---------- Calendar store 边界 ----------
  it('calendar.pushEvents 空数组 no-op', () => {
    const cal = useCalendarStore();
    cal.pushEvents([]);
    expect(cal.events).toEqual([]);
  });

  it('calendar.pushEvents 去重（按 id）', () => {
    const cal = useCalendarStore();
    const ev: WorldEventDto = {
      id: 1,
      eventType: 'NPC_GREET',
      payload: {},
      isProcessed: false,
      createdAt: '2026-01-01T00:00:00Z',
    };
    cal.pushEvents([ev]);
    cal.pushEvents([ev]);
    expect(cal.events.length).toBe(1);
  });

  it('calendar.pushEvents 同时追加 + 去重', () => {
    const cal = useCalendarStore();
    const e1: WorldEventDto = { id: 1, eventType: 'A', payload: {}, isProcessed: false, createdAt: '' };
    const e2: WorldEventDto = { id: 2, eventType: 'B', payload: {}, isProcessed: false, createdAt: '' };
    const e3: WorldEventDto = { id: 3, eventType: 'C', payload: {}, isProcessed: false, createdAt: '' };
    cal.pushEvents([e1, e2]);
    cal.pushEvents([e2, e3]);
    expect(cal.events.map((e) => e.id).sort()).toEqual([1, 2, 3]);
  });

  it('calendar.setBootstrap 替换 today + events', () => {
    const cal = useCalendarStore();
    const today: WorldCalendarDto = {
      dayIndex: 5,
      weekday: 'monday',
      monthIndex: 1,
      season: 'spring',
      timeOfDay: 'MORNING',
      weather: 'SUNNY',
    };
    cal.setBootstrap(today, []);
    expect(cal.today?.dayIndex).toBe(5);
  });
});
