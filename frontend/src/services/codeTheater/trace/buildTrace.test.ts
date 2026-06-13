import { describe, expect, it } from 'vitest';
import { MAX_TRACE_STEPS } from '../types';
import type { TraceEvent } from '../types';
import { buildTrace } from './buildTrace';
import { branchRaw, forLoopRaw, makeOverBudgetRaw, sequentialRaw } from './fixtures';
import type { RawTracePayload } from './rawTypes';

describe('buildTrace —— 顺序执行', () => {
  it('line/assign/output 原样整形并自增 step', () => {
    const source = 'x = 5\ny = x + 2\nprint(y)\n';
    const trace = buildTrace(sequentialRaw, source);

    expect(trace.version).toBe(1);
    expect(trace.source).toBe(source);
    expect(trace.stdout).toBe('7\n');
    expect(trace.truncated).toBe(false);
    expect(trace.stepCount).toBe(6);
    expect(trace.events).toEqual<TraceEvent[]>([
      { step: 0, line: 1, kind: 'line' },
      { step: 1, line: 1, kind: 'assign', varName: 'x', value: '5', scope: 'global' },
      { step: 2, line: 2, kind: 'line' },
      { step: 3, line: 2, kind: 'assign', varName: 'y', value: '7', scope: 'global' },
      { step: 4, line: 3, kind: 'line' },
      { step: 5, line: 3, kind: 'output', text: '7' },
    ]);
  });
});

describe('buildTrace —— for + range', () => {
  it('保留 loop_iter（iter/varName/value/loopId）并对齐步号', () => {
    const trace = buildTrace(forLoopRaw, 'for i in range(2):\n    print(i)\n');

    expect(trace.stepCount).toBe(9);
    expect(trace.truncated).toBe(false);
    expect(trace.events).toEqual<TraceEvent[]>([
      { step: 0, line: 1, kind: 'line' },
      { step: 1, line: 1, kind: 'loop_iter', loopId: 'for@1', loopType: 'for', iter: 0, varName: 'i', value: '0' },
      { step: 2, line: 2, kind: 'line' },
      { step: 3, line: 2, kind: 'output', text: '0' },
      { step: 4, line: 1, kind: 'line' },
      { step: 5, line: 1, kind: 'loop_iter', loopId: 'for@1', loopType: 'for', iter: 1, varName: 'i', value: '1' },
      { step: 6, line: 2, kind: 'line' },
      { step: 7, line: 2, kind: 'output', text: '1' },
      { step: 8, line: 1, kind: 'line' },
    ]);

    const iters = trace.events.filter((e) => e.kind === 'loop_iter');
    expect(iters.map((e) => (e.kind === 'loop_iter' ? e.value : null))).toEqual(['0', '1']);
  });
});

describe('buildTrace —— 分支', () => {
  it('保留 branch（cond/result/lineTaken）字段', () => {
    const trace = buildTrace(branchRaw, 'x = 2\nif x > 3:\n    print("big")\nelse:\n    print("small")\n');

    expect(trace.stepCount).toBe(6);
    expect(trace.events).toEqual<TraceEvent[]>([
      { step: 0, line: 1, kind: 'line' },
      { step: 1, line: 1, kind: 'assign', varName: 'x', value: '2', scope: 'global' },
      { step: 2, line: 2, kind: 'line' },
      { step: 3, line: 2, kind: 'branch', cond: 'x > 3', result: false, lineTaken: 5 },
      { step: 4, line: 5, kind: 'line' },
      { step: 5, line: 5, kind: 'output', text: 'small' },
    ]);
  });
});

describe('buildTrace —— 死循环截断（MAX_TRACE_STEPS）', () => {
  it('超预算时截断到 MAX_TRACE_STEPS 并置 truncated=true', () => {
    const trace = buildTrace(makeOverBudgetRaw(5), 'while True:\n    n += 1\n');

    expect(trace.truncated).toBe(true);
    expect(trace.events.length).toBe(MAX_TRACE_STEPS);
    expect(trace.stepCount).toBe(MAX_TRACE_STEPS);
    expect(trace.events[0]).toEqual({ step: 0, line: 1, kind: 'line' });
    const last = trace.events[trace.events.length - 1];
    expect(last?.step).toBe(MAX_TRACE_STEPS - 1);
  });

  it('恰好 MAX_TRACE_STEPS 条且 harness 未截断时不算截断', () => {
    const records = Array.from({ length: MAX_TRACE_STEPS }, () => ({ kind: 'line' as const, line: 1 }));
    const raw: RawTracePayload = { records, stdout: '', truncated: false };
    const trace = buildTrace(raw, '');

    expect(trace.events.length).toBe(MAX_TRACE_STEPS);
    expect(trace.truncated).toBe(false);
  });

  it('harness 侧 truncated=true 即使记录很少也透传', () => {
    const trace = buildTrace({ records: [{ kind: 'line', line: 1 }], stdout: '', truncated: true }, '');
    expect(trace.truncated).toBe(true);
    expect(trace.events.length).toBe(1);
  });
});

describe('buildTrace —— 优雅降级与字段防御', () => {
  it('缺字段 / 未知 kind 降级为 line 事件，非法行号回退 1', () => {
    const messy = {
      records: [
        { kind: 'assign', line: 2 }, // 缺 varName → line
        { kind: 'mystery', line: 3 }, // 未知 kind → line
        { kind: 'loop_iter', line: 4 }, // 缺 loopId → line
        { kind: 'call', line: 5 }, // 缺 funcName → line
        { kind: 'output', line: '7', text: 7 }, // 行号/文本强制转换
        { kind: 'line', line: 0 }, // 非法行号 → 1
        { kind: 'line' }, // 缺行号 → 1
      ],
      stdout: '',
      truncated: false,
    } as unknown as RawTracePayload;

    const trace = buildTrace(messy, 'whatever');

    expect(trace.events).toEqual<TraceEvent[]>([
      { step: 0, line: 2, kind: 'line' },
      { step: 1, line: 3, kind: 'line' },
      { step: 2, line: 4, kind: 'line' },
      { step: 3, line: 5, kind: 'line' },
      { step: 4, line: 7, kind: 'output', text: '7' },
      { step: 5, line: 1, kind: 'line' },
      { step: 6, line: 1, kind: 'line' },
    ]);
  });

  it('空记录产出空 events 的合法 Trace', () => {
    const trace = buildTrace({ records: [], stdout: '', truncated: false }, 'x = 1');
    expect(trace.events).toEqual([]);
    expect(trace.stepCount).toBe(0);
    expect(trace.version).toBe(1);
    expect(trace.source).toBe('x = 1');
  });
});

describe('buildTrace —— 其余 kind 字段严格对齐 types.ts', () => {
  it('call/return/list_op/dict_op/input/exception 字段直通且可选字段按存在性裁剪', () => {
    const raw = {
      records: [
        { kind: 'call', line: 1, funcName: 'add', args: ['2', '3'], depth: 1 },
        { kind: 'return', line: 2, funcName: 'add', value: '5', depth: 0 },
        { kind: 'list_op', line: 3, op: 'append', target: 'xs', value: '9', lengthAfter: 3 },
        { kind: 'list_op', line: 3, op: 'index', target: 'xs', position: 0, lengthAfter: 3 },
        { kind: 'dict_op', line: 4, op: 'set', target: 'd', key: 'a', value: '1' },
        { kind: 'input', line: 5, prompt: '?', value: '42' },
        { kind: 'exception', line: 6, excType: 'ZeroDivisionError', message: 'division by zero' },
      ],
      stdout: '',
      truncated: false,
    } as unknown as RawTracePayload;

    const trace = buildTrace(raw, 'src');

    expect(trace.events).toEqual<TraceEvent[]>([
      { step: 0, line: 1, kind: 'call', funcName: 'add', args: ['2', '3'], depth: 1 },
      { step: 1, line: 2, kind: 'return', funcName: 'add', value: '5', depth: 0 },
      { step: 2, line: 3, kind: 'list_op', op: 'append', target: 'xs', value: '9', lengthAfter: 3 },
      { step: 3, line: 3, kind: 'list_op', op: 'index', target: 'xs', position: 0, lengthAfter: 3 },
      { step: 4, line: 4, kind: 'dict_op', op: 'set', target: 'd', key: 'a', value: '1' },
      { step: 5, line: 5, kind: 'input', prompt: '?', value: '42' },
      { step: 6, line: 6, kind: 'exception', excType: 'ZeroDivisionError', message: 'division by zero' },
    ]);
  });

  it('未知 list_op/dict_op 操作符或空 excType 安全降级', () => {
    const raw = {
      records: [
        { kind: 'list_op', line: 1, op: 'frobnicate', target: 'xs', lengthAfter: 1 }, // 非法 op → line
        { kind: 'exception', line: 2, excType: '', message: 'boom' }, // 空 excType → 'Exception'
      ],
      stdout: '',
      truncated: false,
    } as unknown as RawTracePayload;

    const trace = buildTrace(raw, '');
    expect(trace.events).toEqual<TraceEvent[]>([
      { step: 0, line: 1, kind: 'line' },
      { step: 1, line: 2, kind: 'exception', excType: 'Exception', message: 'boom' },
    ]);
  });
});
