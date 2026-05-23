import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  DEFAULT_PYODIDE_BUDGET,
  PyodideBudgetGuard,
} from '@/services/pyodideBudget';

// 锚一个 UTC+8 自然日中段时刻，避免接近 0 点跨日导致 day key 跳变。
const FIXED_NOW = new Date('2026-05-24T04:00:00.000Z').getTime();

function freezeTime(at: number = FIXED_NOW): void {
  vi.useFakeTimers();
  vi.setSystemTime(at);
}

describe('PyodideBudgetGuard.canExecute', () => {
  beforeEach(() => freezeTime());
  afterEach(() => vi.useRealTimers());

  it('默认配置首次执行被放行（无 reason 字段）', () => {
    const guard = new PyodideBudgetGuard();
    const result = guard.canExecute();
    expect(result.allowed).toBe(true);
    expect(result.reason).toBeUndefined();
  });

  it('当分钟内累计达到 perMinuteLimit 时拒绝（minute_limit_exceeded）', () => {
    const guard = new PyodideBudgetGuard({ perMinuteLimit: 3 });
    guard.recordExecution(10, 'ok');
    guard.recordExecution(10, 'ok');
    guard.recordExecution(10, 'ok');
    const result = guard.canExecute();
    expect(result).toEqual({ allowed: false, reason: 'minute_limit_exceeded' });
  });

  it('分钟跳变后旧分钟计数不再计入新窗口', () => {
    const guard = new PyodideBudgetGuard({ perMinuteLimit: 2 });
    guard.recordExecution(5, 'ok');
    guard.recordExecution(5, 'ok');
    expect(guard.canExecute().allowed).toBe(false);
    vi.setSystemTime(FIXED_NOW + 61_000);
    expect(guard.canExecute().allowed).toBe(true);
  });

  it('当日累计达到 perDayLimit 时拒绝（day_limit_exceeded）', () => {
    const guard = new PyodideBudgetGuard({ perMinuteLimit: 9_999, perDayLimit: 5 });
    for (let i = 0; i < 5; i += 1) {
      guard.recordExecution(1, 'ok');
    }
    expect(guard.canExecute()).toEqual({ allowed: false, reason: 'day_limit_exceeded' });
  });

  it('分钟限额先于日限额触发：minute 满即 reason=minute_limit_exceeded', () => {
    const guard = new PyodideBudgetGuard({ perMinuteLimit: 1, perDayLimit: 100 });
    guard.recordExecution(1, 'ok');
    expect(guard.canExecute().reason).toBe('minute_limit_exceeded');
  });
});

describe('PyodideBudgetGuard.recordExecution', () => {
  beforeEach(() => freezeTime());
  afterEach(() => vi.useRealTimers());

  it('累计写入分钟桶、日桶与历史', () => {
    const guard = new PyodideBudgetGuard();
    guard.recordExecution(120, 'ok');
    guard.recordExecution(200, 'timeout');
    const snap = guard.snapshot();
    expect(snap.minuteBuckets).toBe(1);
    expect(snap.dayBuckets).toBe(1);
    expect(snap.historyLength).toBe(2);
  });

  it('跨分钟执行生成两个分钟桶但仍同一日桶', () => {
    const guard = new PyodideBudgetGuard();
    guard.recordExecution(10, 'ok');
    vi.setSystemTime(FIXED_NOW + 70_000);
    guard.recordExecution(10, 'error');
    const snap = guard.snapshot();
    expect(snap.minuteBuckets).toBe(2);
    expect(snap.dayBuckets).toBe(1);
  });
});

describe('PyodideBudgetGuard.reset*', () => {
  beforeEach(() => freezeTime());
  afterEach(() => vi.useRealTimers());

  it('resetMinute 不影响日计数', () => {
    const guard = new PyodideBudgetGuard({ perMinuteLimit: 2, perDayLimit: 5 });
    guard.recordExecution(1, 'ok');
    guard.recordExecution(1, 'ok');
    expect(guard.canExecute().reason).toBe('minute_limit_exceeded');
    guard.resetMinute();
    const result = guard.canExecute();
    expect(result.allowed).toBe(true);
    expect(guard.snapshot().dayBuckets).toBe(1);
  });

  it('resetDaily 清空日计数后允许继续执行', () => {
    const guard = new PyodideBudgetGuard({ perDayLimit: 2 });
    guard.recordExecution(1, 'ok');
    guard.recordExecution(1, 'ok');
    expect(guard.canExecute().reason).toBe('day_limit_exceeded');
    guard.resetDaily();
    expect(guard.canExecute().allowed).toBe(true);
  });
});

describe('PyodideBudgetGuard.staticScanCode 黑名单 import', () => {
  it.each<[string, string]>([
    ['import socket', 'socket'],
    ['import urllib.request', 'urllib.request'],
    ['from requests import get', 'requests'],
    ['import subprocess as sp', 'subprocess'],
    ['from threading import Thread', 'threading'],
  ])('阻断 `%s`，消息含模块名 %s', (code, expectedFragment) => {
    const guard = new PyodideBudgetGuard();
    const result = guard.staticScanCode(code);
    expect(result.blocked.length).toBeGreaterThanOrEqual(1);
    expect(result.blocked.some((m) => m.includes(expectedFragment))).toBe(true);
    expect(result.warnings).toEqual([]);
  });

  it('阻断 `os.system(...)` 字面调用（不依赖 import os）', () => {
    const guard = new PyodideBudgetGuard();
    const result = guard.staticScanCode('os.system("rm -rf /")');
    expect(result.blocked.some((m) => m.includes('os.system'))).toBe(true);
  });

  it('未命中黑名单时不返回阻断项', () => {
    const guard = new PyodideBudgetGuard();
    const result = guard.staticScanCode('import math\nimport random\nprint(math.pi)');
    expect(result.blocked).toEqual([]);
    expect(result.warnings).toEqual([]);
  });
});

describe('PyodideBudgetGuard.staticScanCode 模式启发', () => {
  it('`while True: pass` 无 break/return/raise 触发 PB-004 警告', () => {
    const guard = new PyodideBudgetGuard();
    const result = guard.staticScanCode('while True:\n    pass\n');
    expect(result.warnings.some((m) => m.startsWith('PB-004'))).toBe(true);
    expect(result.blocked).toEqual([]);
  });

  it('`while True:` 内含 break 不触发 PB-004', () => {
    const guard = new PyodideBudgetGuard();
    const result = guard.staticScanCode('while True:\n    if x > 0:\n        break\n');
    expect(result.warnings.some((m) => m.startsWith('PB-004'))).toBe(false);
  });

  it('`range(10**8)` 触发 PB-005 警告', () => {
    const guard = new PyodideBudgetGuard();
    const result = guard.staticScanCode('for _ in range(10**8):\n    pass\n');
    expect(result.warnings.some((m) => m.startsWith('PB-005'))).toBe(true);
  });

  it('`range(10**6)` 低于阈值 N>=7，不触发 PB-005', () => {
    const guard = new PyodideBudgetGuard();
    const result = guard.staticScanCode('for _ in range(10**6):\n    pass\n');
    expect(result.warnings.some((m) => m.startsWith('PB-005'))).toBe(false);
  });

  it('同时含死循环 + 黑名单 import：blocked + warnings 都填充', () => {
    const guard = new PyodideBudgetGuard();
    const result = guard.staticScanCode('import socket\nwhile True:\n    pass\n');
    expect(result.blocked.some((m) => m.includes('socket'))).toBe(true);
    expect(result.warnings.some((m) => m.startsWith('PB-004'))).toBe(true);
  });
});

describe('PyodideBudgetGuard 配置注入', () => {
  it('partial 配置只覆盖指定字段，其余取默认值', () => {
    const guard = new PyodideBudgetGuard({ perMinuteLimit: 3 });
    expect(guard.config.perMinuteLimit).toBe(3);
    expect(guard.config.perDayLimit).toBe(DEFAULT_PYODIDE_BUDGET.perDayLimit);
    expect(guard.config.cpuTimeoutMs).toBe(DEFAULT_PYODIDE_BUDGET.cpuTimeoutMs);
    expect(guard.config.memoryLimitMb).toBe(DEFAULT_PYODIDE_BUDGET.memoryLimitMb);
  });

  it('DEFAULT_PYODIDE_BUDGET 锁定设计 §2 基准参数', () => {
    expect(DEFAULT_PYODIDE_BUDGET).toEqual({
      cpuTimeoutMs: 5_000,
      memoryLimitMb: 64,
      perMinuteLimit: 20,
      perDayLimit: 200,
    });
  });
});
