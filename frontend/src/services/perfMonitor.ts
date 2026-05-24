/**
 * Pythontopia 性能监控埋点（W4 性能基线骨架，纯逻辑层）。
 *
 * 设计依据：`docs/performance-baseline.md` §3 Web Vitals / §4 Phaser 场景 / §11 工具栈。
 * 仅负责"埋点 + 阈值告警 + 内存报表"三件事：
 *   1. `start/end(label)` 给业务任意区段计时（场景切换 / API 调用 / Pyodide 加载）；
 *   2. `recordWebVital(name, value)` 在收到 Lighthouse / web-vitals 上报或自测脚本回填时入账；
 *   3. `getReport()` 给班级 dashboard / 开发面板提供只读快照。
 *
 * 显式 **不** 做的事：
 *   - 不引入 `web-vitals` / `Sentry` / `OpenTelemetry` 第三方依赖（保持 frontend 体积预算）；
 *   - 不直接读 `PerformanceObserver`（由调用方在 `main.ts` 接入后转发到本模块）；
 *   - 不做持久化（落盘 / 上报后端）；那部分由 R-13 `analyticsBridge.ts` 接管。
 *
 * 所有阈值与 `frontend/perf-budget.json.webVitals` 单一来源对齐；如改阈值，两处同步。
 */

export type WebVitalName = 'LCP' | 'FCP' | 'INP' | 'CLS' | 'TTI' | 'TTFB';

export type WebVitalRating = 'good' | 'needs-improvement' | 'poor';

export interface WebVitalThreshold {
  /** good 上限：≤ good 视为达标 */
  good: number;
  /** poor 下限：> poor 视为不达标，触发 onAlert */
  poor: number;
  /** 单位说明，仅做记录用，不参与判定 */
  unit: 'ms' | 'score';
}

/**
 * Web Vitals 阈值表，与 `perf-budget.json.webVitals` 同步维护。
 * 数据源：web.dev / Core Web Vitals 官方文档 + W4 plan 教育场景调优（LCP 2.5s / INP 200ms 与官方一致；TTI 取 4G 移动设备 3.8s）。
 */
export const WEB_VITAL_THRESHOLDS: Readonly<Record<WebVitalName, WebVitalThreshold>> = Object.freeze({
  LCP: { good: 2500, poor: 4000, unit: 'ms' },
  FCP: { good: 1800, poor: 3000, unit: 'ms' },
  INP: { good: 200, poor: 500, unit: 'ms' },
  CLS: { good: 0.1, poor: 0.25, unit: 'score' },
  TTI: { good: 3800, poor: 7300, unit: 'ms' },
  TTFB: { good: 800, poor: 1800, unit: 'ms' },
});

export interface DurationRecord {
  /** 业务标签，例如 `scene_transition:town_hub→farm_north` */
  label: string;
  /** 毫秒 */
  durationMs: number;
  /** 该次 end 调用时的高精时间戳，便于绘制 timeline */
  endedAt: number;
}

export interface WebVitalRecord {
  name: WebVitalName;
  value: number;
  rating: WebVitalRating;
  /** 触发 onAlert 时为 true（rating === 'poor'） */
  alerted: boolean;
  recordedAt: number;
}

export interface PerfReport {
  /** 当前仍在计时的 label 与对应 start 时间戳（用于排查泄漏） */
  pendingMarks: Record<string, number>;
  /** 累积的 end 记录，按 label 聚合统计 */
  durations: PerfDurationSummary[];
  /** 累积的 Web Vitals 记录（保留全量） */
  vitals: WebVitalRecord[];
  /** Web Vitals 最新一条按 name 聚合的评级，用于面板红绿灯 */
  vitalsLatestRating: Partial<Record<WebVitalName, WebVitalRating>>;
  /** 此次 getReport 调用的时间戳 */
  generatedAt: number;
}

export interface PerfDurationSummary {
  label: string;
  count: number;
  totalMs: number;
  avgMs: number;
  /** 简易 p95：当 count<20 时取 max 兜底（教学场景样本本就少） */
  p95Ms: number;
  maxMs: number;
}

export interface PerfMonitorOptions {
  /**
   * 自定义时间源，默认优先 `performance.now()`，降级 `Date.now()`。
   * 测试时通过传入 mock 函数避免动 `globalThis.performance`。
   */
  now?: () => number;
  /**
   * 当 Web Vital 评级为 'poor' 时回调，默认 `console.warn`。
   * 显式传 `null` 可静默（用于测试或生产关闭日志）。
   */
  onAlert?: ((record: WebVitalRecord) => void) | null;
  /**
   * 当对同一 label 重复 `start` 时回调，默认 `console.warn`；
   * 表达"覆盖原 start 标记"的语义，避免静默掉旧标记。
   */
  onDuplicateStart?: ((label: string, previousStart: number) => void) | null;
}

const DEFAULT_NOW: () => number = () => {
  if (typeof performance !== 'undefined' && typeof performance.now === 'function') {
    return performance.now();
  }
  return Date.now();
};

function rate(name: WebVitalName, value: number): WebVitalRating {
  const t = WEB_VITAL_THRESHOLDS[name];
  if (value <= t.good) return 'good';
  if (value <= t.poor) return 'needs-improvement';
  return 'poor';
}

function computeP95(sorted: number[]): number {
  if (sorted.length === 0) return 0;
  if (sorted.length < 20) {
    return sorted[sorted.length - 1] ?? 0;
  }
  const idx = Math.min(sorted.length - 1, Math.ceil(sorted.length * 0.95) - 1);
  return sorted[idx] ?? 0;
}

/**
 * 性能监控器：单例与多实例均可。
 *
 * 单例用法（业务侧）：
 * ```ts
 * import { perfMonitor } from '@/services/perfMonitor';
 * perfMonitor.start('scene_transition:town_hub→farm_north');
 * await switchScene();
 * perfMonitor.end('scene_transition:town_hub→farm_north');
 * ```
 *
 * 多实例用法（测试 / 隔离场景）：直接 `new PerfMonitor({ now: fakeNow })`。
 */
export class PerfMonitor {
  private readonly now: () => number;
  private readonly onAlert: ((record: WebVitalRecord) => void) | null;
  private readonly onDuplicateStart: ((label: string, previousStart: number) => void) | null;

  /** key: label，value: 对应的 start 高精时间戳 */
  private readonly marks = new Map<string, number>();
  /** 完整 end 历史，按时序追加 */
  private readonly durations: DurationRecord[] = [];
  /** 完整 web vital 历史，按时序追加 */
  private readonly vitals: WebVitalRecord[] = [];

  constructor(options: PerfMonitorOptions = {}) {
    this.now = options.now ?? DEFAULT_NOW;
    this.onAlert = resolveOnAlert(options);
    this.onDuplicateStart = resolveOnDuplicateStart(options);
  }

  /**
   * 开始计时。
   * 如果同 label 已存在 start 标记，会触发 `onDuplicateStart` 并覆盖旧标记，
   * 避免"漏 end → 永远显示在 pendingMarks"的内存泄漏。
   */
  start(label: string): void {
    assertNonEmptyLabel(label);
    const previous = this.marks.get(label);
    if (previous !== undefined && this.onDuplicateStart) {
      this.onDuplicateStart(label, previous);
    }
    this.marks.set(label, this.now());
  }

  /**
   * 结束计时并返回 durationMs。
   * 未配对 start 的 end 调用返回 `null` 而非抛错——
   * 教学场景常因路由切换/组件卸载错过 start，不应阻断业务。
   */
  end(label: string): number | null {
    assertNonEmptyLabel(label);
    const startTs = this.marks.get(label);
    if (startTs === undefined) {
      return null;
    }
    const endedAt = this.now();
    const durationMs = Math.max(0, endedAt - startTs);
    this.marks.delete(label);
    this.durations.push({ label, durationMs, endedAt });
    return durationMs;
  }

  /**
   * 直接记录一段已知耗时（不走 start/end 配对），例如从 `PerformanceObserver` 转发来的 `paint` 条目。
   */
  record(label: string, durationMs: number): void {
    assertNonEmptyLabel(label);
    if (!Number.isFinite(durationMs) || durationMs < 0) {
      throw new RangeError(`perfMonitor.record: durationMs must be finite & >=0, got ${durationMs}`);
    }
    this.durations.push({ label, durationMs, endedAt: this.now() });
  }

  /**
   * 上报 Web Vital；自动按 `WEB_VITAL_THRESHOLDS` 评级。
   * rating === 'poor' 时触发 `onAlert`（默认 `console.warn`），并把 `alerted=true` 落盘。
   */
  recordWebVital(name: WebVitalName, value: number): WebVitalRecord {
    if (!(name in WEB_VITAL_THRESHOLDS)) {
      throw new RangeError(`perfMonitor.recordWebVital: unknown vital "${name}"`);
    }
    if (!Number.isFinite(value) || value < 0) {
      throw new RangeError(`perfMonitor.recordWebVital: value must be finite & >=0, got ${value}`);
    }
    const rating = rate(name, value);
    const record: WebVitalRecord = {
      name,
      value,
      rating,
      alerted: rating === 'poor',
      recordedAt: this.now(),
    };
    this.vitals.push(record);
    if (rating === 'poor' && this.onAlert) {
      this.onAlert(record);
    }
    return record;
  }

  /** 取只读快照；调用方可安全地遍历不影响内部状态。 */
  getReport(): PerfReport {
    const pendingMarks: Record<string, number> = {};
    for (const [label, ts] of this.marks) {
      pendingMarks[label] = ts;
    }

    const grouped = new Map<string, number[]>();
    for (const d of this.durations) {
      const arr = grouped.get(d.label);
      if (arr) arr.push(d.durationMs);
      else grouped.set(d.label, [d.durationMs]);
    }
    const durationsSummary: PerfDurationSummary[] = [];
    for (const [label, values] of grouped) {
      const sorted = values.slice().sort((a, b) => a - b);
      const total = sorted.reduce((acc, v) => acc + v, 0);
      durationsSummary.push({
        label,
        count: sorted.length,
        totalMs: total,
        avgMs: total / sorted.length,
        p95Ms: computeP95(sorted),
        maxMs: sorted[sorted.length - 1] ?? 0,
      });
    }
    durationsSummary.sort((a, b) => a.label.localeCompare(b.label));

    const vitalsLatestRating: Partial<Record<WebVitalName, WebVitalRating>> = {};
    for (const v of this.vitals) {
      vitalsLatestRating[v.name] = v.rating;
    }

    return {
      pendingMarks,
      durations: durationsSummary,
      vitals: this.vitals.slice(),
      vitalsLatestRating,
      generatedAt: this.now(),
    };
  }

  /** 清空所有 marks / durations / vitals；用于路由切换或测试隔离。 */
  clear(): void {
    this.marks.clear();
    this.durations.length = 0;
    this.vitals.length = 0;
  }

  /** 仅供 dashboard 调试展示，业务层别依赖具体形状。 */
  snapshot(): { pendingMarks: number; durations: number; vitals: number } {
    return {
      pendingMarks: this.marks.size,
      durations: this.durations.length,
      vitals: this.vitals.length,
    };
  }
}

function resolveOnAlert(
  options: PerfMonitorOptions,
): ((record: WebVitalRecord) => void) | null {
  if (options.onAlert === null) return null;
  if (options.onAlert !== undefined) return options.onAlert;
  return (record) => {
    console.warn(
      `[perfMonitor] Web Vital "${record.name}" exceeded poor threshold: ${record.value} ` +
        `(threshold ${WEB_VITAL_THRESHOLDS[record.name].poor} ${WEB_VITAL_THRESHOLDS[record.name].unit})`,
    );
  };
}

function resolveOnDuplicateStart(
  options: PerfMonitorOptions,
): ((label: string, previousStart: number) => void) | null {
  if (options.onDuplicateStart === null) return null;
  if (options.onDuplicateStart !== undefined) return options.onDuplicateStart;
  return (label, previousStart) => {
    console.warn(
      `[perfMonitor] start("${label}") called twice without end; previous start at ${previousStart} discarded`,
    );
  };
}

function assertNonEmptyLabel(label: string): void {
  if (typeof label !== 'string' || label.length === 0) {
    throw new TypeError('perfMonitor: label must be a non-empty string');
  }
}

/**
 * 全局单例。
 *
 * 业务层默认引用此实例；如需在测试或隔离上下文中工作，请改用 `new PerfMonitor()`。
 * 注意：HMR 下 Vite 会保留模块作用域，单例会跨热更新存活，需要在 router beforeEach 主动 `clear()`。
 */
export const perfMonitor = new PerfMonitor();
