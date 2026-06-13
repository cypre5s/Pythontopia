import { describe, expect, it } from 'vitest';

import {
  locateDiagnosticFocus,
  sliceTraceAroundFocus,
} from '@/services/codeTheater/debug';
import { MAX_TRACE_STEPS } from '@/services/codeTheater/types';
import type {
  AssignEvent,
  ExceptionEvent,
  LineEvent,
  LoopIterEvent,
  OutputEvent,
  Trace,
  TraceEvent,
} from '@/services/codeTheater/types';

function assign(step: number, line: number, varName: string, value: string): AssignEvent {
  return { step, line, kind: 'assign', varName, value, scope: 'global' };
}

function loopIter(
  step: number,
  line: number,
  loopId: string,
  iter: number,
  varName?: string,
  value?: string,
): LoopIterEvent {
  const base: LoopIterEvent = { step, line, kind: 'loop_iter', loopId, loopType: 'while', iter };
  if (varName != null) base.varName = varName;
  if (value != null) base.value = value;
  return base;
}

function output(step: number, line: number, text: string): OutputEvent {
  return { step, line, kind: 'output', text };
}

function exception(step: number, line: number, excType: string, message: string): ExceptionEvent {
  return { step, line, kind: 'exception', excType, message };
}

function lineEvent(step: number, line: number): LineEvent {
  return { step, line, kind: 'line' };
}

function makeTrace(events: TraceEvent[], overrides: Partial<Trace> = {}): Trace {
  return {
    version: 1,
    source: '',
    events,
    stdout: '',
    truncated: false,
    stepCount: events.length,
    ...overrides,
  };
}

describe('locateDiagnosticFocus · 空轨迹', () => {
  it('空 events 返回 found=false / kind=none', () => {
    const focus = locateDiagnosticFocus(makeTrace([]));
    expect(focus.found).toBe(false);
    expect(focus.kind).toBe('none');
    expect(focus.step).toBe(-1);
    expect(focus.line).toBe(0);
  });
});

describe('locateDiagnosticFocus · 异常绊倒', () => {
  it('定位到异常事件的 step/line/excType，并带出错前变量快照', () => {
    const trace = makeTrace([
      assign(0, 1, 'a', '10'),
      assign(1, 2, 'b', '0'),
      exception(2, 3, 'ZeroDivisionError', 'division by zero'),
    ]);
    const focus = locateDiagnosticFocus(trace);
    expect(focus.found).toBe(true);
    expect(focus.kind).toBe('exception');
    expect(focus.step).toBe(2);
    expect(focus.line).toBe(3);
    expect(focus.excType).toBe('ZeroDivisionError');
    expect(focus.message).toBe('division by zero');
    expect(focus.variables).toEqual({ a: '10', b: '0' });
    expect(focus.phenomenon).toContain('第 3 行');
    expect(focus.phenomenon).toContain('ZeroDivisionError');
    expect(focus.phenomenon).toContain('绊倒');
  });

  it('异常优先级高于死循环截断：两者并存时取异常', () => {
    const trace = makeTrace(
      [loopIter(0, 2, 'while@2', 0), exception(1, 3, 'ValueError', 'bad input')],
      { truncated: true },
    );
    const focus = locateDiagnosticFocus(trace);
    expect(focus.kind).toBe('exception');
    expect(focus.line).toBe(3);
  });
});

describe('locateDiagnosticFocus · 死循环（截断）', () => {
  it('截断 + 循环事件 → kind=infinite_loop，定位到循环行与圈数', () => {
    const events: TraceEvent[] = [assign(0, 1, 'i', '0')];
    for (let iter = 0; iter < 5; iter += 1) {
      events.push(loopIter(iter + 1, 2, 'while@2', iter, 'i', String(iter)));
    }
    const trace = makeTrace(events, { truncated: true });
    const focus = locateDiagnosticFocus(trace);
    expect(focus.kind).toBe('infinite_loop');
    expect(focus.line).toBe(2);
    expect(focus.loopId).toBe('while@2');
    expect(focus.observedIterations).toBe(5);
    expect(focus.phenomenon).toContain('死循环');
    expect(focus.phenomenon).toContain('原地踱步');
    expect(focus.phenomenon).toContain(String(MAX_TRACE_STEPS));
  });

  it('截断但无循环事件 → 仍 infinite_loop，落到最后一步并提示疑似停不下来', () => {
    const trace = makeTrace(
      [lineEvent(0, 1), lineEvent(1, 5), lineEvent(2, 5)],
      { truncated: true },
    );
    const focus = locateDiagnosticFocus(trace);
    expect(focus.kind).toBe('infinite_loop');
    expect(focus.line).toBe(5);
    expect(focus.loopId).toBeUndefined();
    expect(focus.phenomenon).toContain('疑似停不下来');
  });
});

describe('locateDiagnosticFocus · 与期望输出对比', () => {
  it('首个不符的输出 → kind=output_divergence，给出期望/实际与行号', () => {
    const trace = makeTrace([
      output(0, 3, 'hello'),
      output(1, 4, 'word'),
      output(2, 5, 'bye'),
    ]);
    const focus = locateDiagnosticFocus(trace, { expectedStdout: 'hello\nworld\nbye\n' });
    expect(focus.kind).toBe('output_divergence');
    expect(focus.step).toBe(1);
    expect(focus.line).toBe(4);
    expect(focus.expectedLine).toBe('world');
    expect(focus.actualLine).toBe('word');
    expect(focus.phenomenon).toContain('输出跑偏');
  });

  it('输出比期望多 → 命中多出来的那条', () => {
    const trace = makeTrace([output(0, 2, 'a'), output(1, 3, 'b')]);
    const focus = locateDiagnosticFocus(trace, { expectedStdout: 'a\n' });
    expect(focus.kind).toBe('output_divergence');
    expect(focus.line).toBe(3);
    expect(focus.actualLine).toBe('b');
  });

  it('输出比期望少 → 命中缺口', () => {
    const trace = makeTrace([output(0, 2, 'a')]);
    const focus = locateDiagnosticFocus(trace, { expectedStdout: 'a\nb\n' });
    expect(focus.kind).toBe('output_divergence');
    expect(focus.expectedLine).toBe('b');
    expect(focus.phenomenon).toContain('输出跑偏');
  });

  it('输出与期望一致（忽略行尾空白）→ 回退到 last_step', () => {
    const trace = makeTrace([output(0, 2, 'a  '), output(1, 3, 'b')]);
    const focus = locateDiagnosticFocus(trace, { expectedStdout: 'a\nb\n' });
    expect(focus.kind).toBe('last_step');
  });
});

describe('locateDiagnosticFocus · 无明显错误', () => {
  it('无异常 / 无截断 / 无期望 → last_step 落在最后一步', () => {
    const trace = makeTrace([assign(0, 1, 'x', '1'), assign(1, 2, 'y', '2')]);
    const focus = locateDiagnosticFocus(trace);
    expect(focus.kind).toBe('last_step');
    expect(focus.line).toBe(2);
    expect(focus.variables).toEqual({ x: '1', y: '2' });
  });
});

describe('sliceTraceAroundFocus', () => {
  function longTrace(): Trace {
    const events: TraceEvent[] = [];
    for (let step = 0; step < 30; step += 1) {
      events.push(step === 20 ? exception(step, step + 1, 'KeyError', 'k') : lineEvent(step, step + 1));
    }
    return makeTrace(events, { source: 'x\n'.repeat(30) });
  }

  it('截到焦点前后窗口，保留原始 step 不重排', () => {
    const trace = longTrace();
    const focus = locateDiagnosticFocus(trace);
    const slice = sliceTraceAroundFocus(trace, focus, { before: 3, after: 2 });
    expect(slice.trace.events).toHaveLength(6);
    expect(slice.startStep).toBe(17);
    expect(slice.endStep).toBe(22);
    expect(slice.focusStep).toBe(20);
    expect(slice.focusIndex).toBe(3);
    expect(slice.trace.events[slice.focusIndex]?.step).toBe(20);
    expect(slice.trace.stepCount).toBe(6);
    // 原始 step 连续递增，未被重排为 0..n
    expect(slice.trace.events.map((event) => event.step)).toEqual([17, 18, 19, 20, 21, 22]);
  });

  it('焦点贴近开头时左端 clamp 到 0', () => {
    const trace = makeTrace([
      exception(0, 1, 'E', 'm'),
      lineEvent(1, 2),
      lineEvent(2, 3),
    ]);
    const focus = locateDiagnosticFocus(trace);
    const slice = sliceTraceAroundFocus(trace, focus, { before: 8, after: 1 });
    expect(slice.startStep).toBe(0);
    expect(slice.focusIndex).toBe(0);
    expect(slice.trace.events).toHaveLength(2);
  });

  it('空轨迹返回空切片', () => {
    const focus = locateDiagnosticFocus(makeTrace([]));
    const slice = sliceTraceAroundFocus(makeTrace([]), focus);
    expect(slice.trace.events).toHaveLength(0);
    expect(slice.focusIndex).toBe(-1);
    expect(slice.startStep).toBe(-1);
  });
});
