import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  PerfMonitor,
  WEB_VITAL_THRESHOLDS,
  perfMonitor,
} from '@/services/perfMonitor';
import type { WebVitalRecord } from '@/services/perfMonitor';

/**
 * 时间源 mock 工厂：返回一个可手动推进的 `now()`，
 * 测试无需 vi.useFakeTimers（perfMonitor 自身只读时间戳，不依赖 setTimeout）。
 */
function fakeClock(initial = 1_000): { now: () => number; advance: (ms: number) => void } {
  let t = initial;
  return {
    now: () => t,
    advance: (ms: number) => {
      t += ms;
    },
  };
}

describe('PerfMonitor.start / end 基本配对', () => {
  it('end 返回 start 与 end 之间的毫秒差', () => {
    const clock = fakeClock(1_000);
    const monitor = new PerfMonitor({ now: clock.now });

    monitor.start('scene_transition');
    clock.advance(420);
    const duration = monitor.end('scene_transition');

    expect(duration).toBe(420);
    expect(monitor.snapshot().pendingMarks).toBe(0);
    expect(monitor.snapshot().durations).toBe(1);
  });

  it('end 在缺失 start 时返回 null 且不抛错', () => {
    const monitor = new PerfMonitor({ now: () => 0 });
    expect(monitor.end('missing_label')).toBeNull();
    expect(monitor.snapshot().durations).toBe(0);
  });

  it('end 完成后再次 end 同 label 返回 null（marks 已被消费）', () => {
    const clock = fakeClock();
    const monitor = new PerfMonitor({ now: clock.now });
    monitor.start('api_call');
    clock.advance(80);
    expect(monitor.end('api_call')).toBe(80);
    expect(monitor.end('api_call')).toBeNull();
  });
});

describe('PerfMonitor.start 重复调用', () => {
  it('重复 start 同一 label 触发 onDuplicateStart 并覆盖旧 start', () => {
    const clock = fakeClock(500);
    const onDup = vi.fn();
    const monitor = new PerfMonitor({ now: clock.now, onDuplicateStart: onDup });

    monitor.start('boot');
    clock.advance(100);
    monitor.start('boot');
    clock.advance(200);
    const duration = monitor.end('boot');

    expect(onDup).toHaveBeenCalledTimes(1);
    expect(onDup).toHaveBeenCalledWith('boot', 500);
    expect(duration).toBe(200);
  });

  it('onDuplicateStart 显式传 null 时静默覆盖（不触发任何回调）', () => {
    const clock = fakeClock(0);
    const monitor = new PerfMonitor({ now: clock.now, onDuplicateStart: null });
    expect(() => {
      monitor.start('x');
      monitor.start('x');
    }).not.toThrow();
    clock.advance(50);
    expect(monitor.end('x')).toBe(50);
  });
});

describe('PerfMonitor.start / end 参数校验', () => {
  it('start 空 label 抛 TypeError', () => {
    const monitor = new PerfMonitor();
    expect(() => monitor.start('')).toThrow(TypeError);
  });

  it('end 空 label 抛 TypeError', () => {
    const monitor = new PerfMonitor();
    expect(() => monitor.end('')).toThrow(TypeError);
  });

  it('record 负 duration 抛 RangeError', () => {
    const monitor = new PerfMonitor();
    expect(() => monitor.record('boot', -1)).toThrow(RangeError);
  });
});

describe('PerfMonitor.recordWebVital 阈值评级', () => {
  it.each<[Parameters<PerfMonitor['recordWebVital']>[0], number, 'good' | 'needs-improvement' | 'poor']>(
    [
      ['LCP', 1_500, 'good'],
      ['LCP', 3_000, 'needs-improvement'],
      ['LCP', 5_000, 'poor'],
      ['FCP', 1_000, 'good'],
      ['FCP', 2_000, 'needs-improvement'],
      ['FCP', 3_500, 'poor'],
      ['INP', 100, 'good'],
      ['INP', 300, 'needs-improvement'],
      ['INP', 700, 'poor'],
      ['CLS', 0.05, 'good'],
      ['CLS', 0.2, 'needs-improvement'],
      ['CLS', 0.4, 'poor'],
    ],
  )('%s=%s → rating=%s', (name, value, expectedRating) => {
    const monitor = new PerfMonitor({ now: () => 1, onAlert: null });
    const record = monitor.recordWebVital(name, value);
    expect(record.rating).toBe(expectedRating);
    expect(record.alerted).toBe(expectedRating === 'poor');
  });

  it('poor 评级触发 onAlert，传入完整 record', () => {
    const alerts: WebVitalRecord[] = [];
    const monitor = new PerfMonitor({
      now: () => 42,
      onAlert: (r) => alerts.push(r),
    });
    monitor.recordWebVital('LCP', 6_000);
    expect(alerts).toHaveLength(1);
    expect(alerts[0]).toMatchObject({
      name: 'LCP',
      value: 6_000,
      rating: 'poor',
      alerted: true,
      recordedAt: 42,
    });
  });

  it('good / needs-improvement 不触发 onAlert', () => {
    const onAlert = vi.fn();
    const monitor = new PerfMonitor({ now: () => 0, onAlert });
    monitor.recordWebVital('LCP', 1_000);
    monitor.recordWebVital('LCP', 3_500);
    expect(onAlert).not.toHaveBeenCalled();
  });

  it('未知 vital 名称抛 RangeError', () => {
    const monitor = new PerfMonitor();
    // @ts-expect-error 测试运行时校验，特意传入错误值
    expect(() => monitor.recordWebVital('NOPE', 100)).toThrow(RangeError);
  });

  it('非有限或负值抛 RangeError', () => {
    const monitor = new PerfMonitor();
    expect(() => monitor.recordWebVital('LCP', Number.NaN)).toThrow(RangeError);
    expect(() => monitor.recordWebVital('LCP', -10)).toThrow(RangeError);
    expect(() => monitor.recordWebVital('LCP', Number.POSITIVE_INFINITY)).toThrow(RangeError);
  });

  it('WEB_VITAL_THRESHOLDS 与 perf-budget.json.webVitals 阈值对齐（防漂移哨兵）', () => {
    expect(WEB_VITAL_THRESHOLDS.LCP).toEqual({ good: 2500, poor: 4000, unit: 'ms' });
    expect(WEB_VITAL_THRESHOLDS.INP).toEqual({ good: 200, poor: 500, unit: 'ms' });
    expect(WEB_VITAL_THRESHOLDS.CLS).toEqual({ good: 0.1, poor: 0.25, unit: 'score' });
  });
});

describe('PerfMonitor.getReport 聚合输出', () => {
  it('聚合同 label 多次 end，计算 count / total / avg / max', () => {
    const clock = fakeClock(0);
    const monitor = new PerfMonitor({ now: clock.now });

    for (const ms of [100, 200, 300]) {
      monitor.start('api');
      clock.advance(ms);
      monitor.end('api');
    }

    const report = monitor.getReport();
    expect(report.durations).toHaveLength(1);
    expect(report.durations[0]).toMatchObject({
      label: 'api',
      count: 3,
      totalMs: 600,
      avgMs: 200,
      maxMs: 300,
    });
  });

  it('pendingMarks 包含未配对的 start', () => {
    const clock = fakeClock(123);
    const monitor = new PerfMonitor({ now: clock.now });
    monitor.start('open_dialog');
    monitor.start('open_panel');

    const report = monitor.getReport();
    expect(report.pendingMarks).toEqual({ open_dialog: 123, open_panel: 123 });
  });

  it('vitalsLatestRating 取最后一条评级', () => {
    const monitor = new PerfMonitor({ now: () => 0, onAlert: null });
    monitor.recordWebVital('LCP', 1_000);
    monitor.recordWebVital('LCP', 5_000);
    const report = monitor.getReport();
    expect(report.vitalsLatestRating.LCP).toBe('poor');
    expect(report.vitals).toHaveLength(2);
  });

  it('durations 列表按 label 字典序稳定排序', () => {
    const clock = fakeClock(0);
    const monitor = new PerfMonitor({ now: clock.now });
    monitor.record('zeta', 1);
    monitor.record('alpha', 2);
    monitor.record('mu', 3);
    const labels = monitor.getReport().durations.map((d) => d.label);
    expect(labels).toEqual(['alpha', 'mu', 'zeta']);
  });

  it('样本数 ≥ 20 时 p95 取第 95 分位而非 max', () => {
    const monitor = new PerfMonitor({ now: () => 0 });
    for (let i = 1; i <= 20; i += 1) {
      monitor.record('api', i * 10);
    }
    const summary = monitor.getReport().durations[0];
    expect(summary).toBeDefined();
    expect(summary?.count).toBe(20);
    expect(summary?.maxMs).toBe(200);
    // 20 样本第 95% 位（index 18，第 19 个值）= 190
    expect(summary?.p95Ms).toBe(190);
  });
});

describe('PerfMonitor.clear', () => {
  it('clear 后 marks / durations / vitals 全部清空', () => {
    const clock = fakeClock(0);
    const monitor = new PerfMonitor({ now: clock.now, onAlert: null });

    monitor.start('a');
    monitor.record('b', 10);
    monitor.recordWebVital('LCP', 5_000);
    expect(monitor.snapshot()).toEqual({ pendingMarks: 1, durations: 1, vitals: 1 });

    monitor.clear();
    expect(monitor.snapshot()).toEqual({ pendingMarks: 0, durations: 0, vitals: 0 });
    expect(monitor.getReport()).toMatchObject({
      pendingMarks: {},
      durations: [],
      vitals: [],
      vitalsLatestRating: {},
    });
  });
});

describe('PerfMonitor 默认时间源 (Performance API mock)', () => {
  const realPerf = globalThis.performance;
  afterEach(() => {
    Object.defineProperty(globalThis, 'performance', {
      configurable: true,
      writable: true,
      value: realPerf,
    });
  });

  it('优先使用 performance.now() 作为时间源', () => {
    const stub = { now: vi.fn(() => 9_999) };
    Object.defineProperty(globalThis, 'performance', {
      configurable: true,
      writable: true,
      value: stub,
    });

    const monitor = new PerfMonitor();
    monitor.start('boot');
    monitor.end('boot');
    expect(stub.now).toHaveBeenCalled();
  });

  it('performance 不存在时降级 Date.now()，仍能产出有效 duration', () => {
    Object.defineProperty(globalThis, 'performance', {
      configurable: true,
      writable: true,
      value: undefined,
    });
    const dateSpy = vi.spyOn(Date, 'now').mockReturnValueOnce(1_000).mockReturnValueOnce(1_500);

    const monitor = new PerfMonitor();
    monitor.start('boot');
    const duration = monitor.end('boot');
    expect(duration).toBe(500);
    dateSpy.mockRestore();
  });
});

describe('PerfMonitor 默认 onAlert 行为', () => {
  it('未传 onAlert 时，poor 评级走 console.warn', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    const monitor = new PerfMonitor({ now: () => 0 });
    monitor.recordWebVital('LCP', 8_000);
    expect(warn).toHaveBeenCalledTimes(1);
    expect(warn.mock.calls[0]?.[0]).toContain('LCP');
    warn.mockRestore();
  });

  it('显式 onAlert: null 时静默（不调 console.warn）', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    const monitor = new PerfMonitor({ now: () => 0, onAlert: null });
    monitor.recordWebVital('LCP', 8_000);
    expect(warn).not.toHaveBeenCalled();
    warn.mockRestore();
  });
});

describe('全局单例 perfMonitor', () => {
  beforeEach(() => perfMonitor.clear());
  afterEach(() => perfMonitor.clear());

  it('导出实例可独立使用，clear 后无残留', () => {
    perfMonitor.record('boot', 250);
    expect(perfMonitor.getReport().durations[0]?.label).toBe('boot');
    perfMonitor.clear();
    expect(perfMonitor.getReport().durations).toEqual([]);
  });
});
