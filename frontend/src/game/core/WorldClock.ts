// W3 WorldClock：玩法时钟核心，统一管理 6 时段 + 4 天气 + 4 季节 + 事件总线。
//
// 设计边界（与现有渲染层 daynight.ts / weather.ts 区分）：
// - WorldClock 是纯前端逻辑层：只关心状态机 / 事件，不调用 Phaser、不调用后端 API。
// - 业务层（stores/world.ts、WorldScene）负责把 WorldClock 的事件分发到 Phaser 渲染、
//   学习反馈、NPC 任务钩子等下游消费者。
// - 后端 dayIndex / weather 通过 syncFromServer 回灌；前端自身按 tick 流逝游戏时间。
// - season、timeOfDay 始终由 dayIndex / hour 派生，不进入持久状态，避免后端误传导致
//   季节与日期错位。
//
// 时段区间约定（左闭右开）：
//   [5, 7)  DAWN       [7, 11) MORNING    [11, 13) NOON
//   [13, 18) AFTERNOON [18, 21) EVENING   其它    NIGHT
//
// 季节排布：每 28 天换季，按 SPRING → SUMMER → AUTUMN → WINTER 循环，112 天为一年。

export type TimeOfDay =
  | 'DAWN'
  | 'MORNING'
  | 'NOON'
  | 'AFTERNOON'
  | 'EVENING'
  | 'NIGHT';

export type Weather = 'SUNNY' | 'CLOUDY' | 'RAINY' | 'SNOWY';

export type Season = 'SPRING' | 'SUMMER' | 'AUTUMN' | 'WINTER';

export interface WorldClockSnapshot {
  /** 第几天，从 1 起算。 */
  dayIndex: number;
  /** 当天浮点小时数 [0, 24)，小数位表示分钟。 */
  hour: number;
  /** 由 hour 派生。 */
  timeOfDay: TimeOfDay;
  weather: Weather;
  /** 由 dayIndex 派生。 */
  season: Season;
}

export type WorldClockEvent =
  | { type: 'time-of-day-changed'; from: TimeOfDay; to: TimeOfDay; snapshot: WorldClockSnapshot }
  | { type: 'weather-changed'; from: Weather; to: Weather; snapshot: WorldClockSnapshot }
  | { type: 'day-changed'; from: number; to: number; snapshot: WorldClockSnapshot }
  | { type: 'season-changed'; from: Season; to: Season; snapshot: WorldClockSnapshot };

type WorldClockListener = (event: WorldClockEvent) => void;

const DEFAULT_DAY_INDEX = 1;
const DEFAULT_HOUR = 8.0;
const DEFAULT_WEATHER: Weather = 'SUNNY';
const HOURS_PER_DAY = 24;
const MINUTES_PER_HOUR = 60;
const MS_PER_SECOND = 1000;
const DAYS_PER_SEASON = 28;
const SEASONS_PER_YEAR = 4;
const DAYS_PER_YEAR = DAYS_PER_SEASON * SEASONS_PER_YEAR;

export class WorldClock {
  private currentDayIndex: number;
  private currentHour: number;
  private currentWeather: Weather;
  private readonly listeners: WorldClockListener[] = [];

  constructor(initial?: Partial<WorldClockSnapshot>) {
    // timeOfDay / season 是派生量，构造函数忽略 patch 上的值，统一从 dayIndex / hour 算。
    this.currentDayIndex = initial?.dayIndex ?? DEFAULT_DAY_INDEX;
    this.currentHour = initial?.hour ?? DEFAULT_HOUR;
    this.currentWeather = initial?.weather ?? DEFAULT_WEATHER;
  }

  snapshot(): WorldClockSnapshot {
    return {
      dayIndex: this.currentDayIndex,
      hour: this.currentHour,
      timeOfDay: WorldClock.timeOfDayOf(this.currentHour),
      weather: this.currentWeather,
      season: WorldClock.seasonOf(this.currentDayIndex),
    };
  }

  /**
   * 业务层每帧调用。
   * @param deltaMs 实际经过的毫秒数。
   * @param gameMinutesPerRealSecond 流速：1 真实秒对应多少游戏分钟（默认 1）。
   */
  tick(deltaMs: number, gameMinutesPerRealSecond: number = 1): void {
    if (!Number.isFinite(deltaMs) || deltaMs <= 0) {
      return;
    }
    if (!Number.isFinite(gameMinutesPerRealSecond) || gameMinutesPerRealSecond <= 0) {
      return;
    }
    const before = this.snapshot();
    const hourDelta =
      (deltaMs / MS_PER_SECOND) * gameMinutesPerRealSecond / MINUTES_PER_HOUR;
    this.currentHour += hourDelta;
    // 用 while 而不是 if，防御一次性传入 >24 小时的 deltaMs（如调试快进）。
    while (this.currentHour >= HOURS_PER_DAY) {
      this.currentHour -= HOURS_PER_DAY;
      this.currentDayIndex += 1;
    }
    this.emitDiff(before);
  }

  /**
   * 从后端 / save 同步状态。timeOfDay / season 是派生量，patch 上的对应字段会被忽略。
   */
  syncFromServer(patch: Partial<WorldClockSnapshot>): void {
    const before = this.snapshot();
    if (patch.dayIndex !== undefined) {
      this.currentDayIndex = patch.dayIndex;
    }
    if (patch.hour !== undefined) {
      this.currentHour = patch.hour;
    }
    if (patch.weather !== undefined) {
      this.currentWeather = patch.weather;
    }
    this.emitDiff(before);
  }

  /**
   * 推进到下一天。不重置 hour（保留 tick 调用时的小数残余，
   * 也允许业务层在任意时点显式跳过整天，由调用方自行决定是否同步重置 hour）。
   */
  advanceDay(): void {
    const before = this.snapshot();
    this.currentDayIndex += 1;
    this.emitDiff(before);
  }

  setWeather(w: Weather): void {
    const before = this.snapshot();
    this.currentWeather = w;
    this.emitDiff(before);
  }

  on(listener: WorldClockListener): () => void {
    this.listeners.push(listener);
    return () => {
      const idx = this.listeners.indexOf(listener);
      if (idx >= 0) {
        this.listeners.splice(idx, 1);
      }
    };
  }

  static timeOfDayOf(hour: number): TimeOfDay {
    // 允许传入跨日 hour（如 25 或 -1），归一化到 [0, 24) 再判断。
    const h = ((hour % HOURS_PER_DAY) + HOURS_PER_DAY) % HOURS_PER_DAY;
    if (h < 5) return 'NIGHT';
    if (h < 7) return 'DAWN';
    if (h < 11) return 'MORNING';
    if (h < 13) return 'NOON';
    if (h < 18) return 'AFTERNOON';
    if (h < 21) return 'EVENING';
    return 'NIGHT';
  }

  static seasonOf(dayIndex: number): Season {
    // 用 Math.floor 把浮点 dayIndex（罕见，但类型允许）落到整数索引上。
    const normalized =
      (((Math.floor(dayIndex) - 1) % DAYS_PER_YEAR) + DAYS_PER_YEAR) % DAYS_PER_YEAR;
    if (normalized < DAYS_PER_SEASON) return 'SPRING';
    if (normalized < DAYS_PER_SEASON * 2) return 'SUMMER';
    if (normalized < DAYS_PER_SEASON * 3) return 'AUTUMN';
    return 'WINTER';
  }

  /**
   * 把变更广播给监听器。事件触发顺序：day → season → timeOfDay → weather，
   * 从最底层（日历推进）到最派生（天气切换），便于下游按层级消费。
   */
  private emitDiff(before: WorldClockSnapshot): void {
    const after = this.snapshot();
    if (before.dayIndex !== after.dayIndex) {
      this.emit({
        type: 'day-changed',
        from: before.dayIndex,
        to: after.dayIndex,
        snapshot: after,
      });
    }
    if (before.season !== after.season) {
      this.emit({
        type: 'season-changed',
        from: before.season,
        to: after.season,
        snapshot: after,
      });
    }
    if (before.timeOfDay !== after.timeOfDay) {
      this.emit({
        type: 'time-of-day-changed',
        from: before.timeOfDay,
        to: after.timeOfDay,
        snapshot: after,
      });
    }
    if (before.weather !== after.weather) {
      this.emit({
        type: 'weather-changed',
        from: before.weather,
        to: after.weather,
        snapshot: after,
      });
    }
  }

  private emit(event: WorldClockEvent): void {
    // 复制一份再遍历，避免监听器在回调里 off 自己时打乱遍历顺序
    // （与 PlayerStateMachine.onChange 的处理方式保持一致）。
    const snapshotListeners = this.listeners.slice();
    for (const listener of snapshotListeners) {
      listener(event);
    }
  }
}
