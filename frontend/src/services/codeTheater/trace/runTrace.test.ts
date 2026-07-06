import { describe, expect, it } from 'vitest';
import type { PyodideRunResult, PyodideRunner } from '@game/adapters/pyodide';
import { MAX_TRACE_STEPS } from '../types';
import type { TraceEvent } from '../types';
import { TRACE_SENTINEL_BEGIN, TRACE_SENTINEL_END, toBase64Utf8 } from './harness';
import { parseRawPayload, runTrace, TraceExecutionError } from './runTrace';

interface FakeRunner extends PyodideRunner {
  lastCode: string | null;
  lastStdin: string | undefined;
}

function fakeRunner(impl: (code: string, stdin: string) => PyodideRunResult): FakeRunner {
  const runner: FakeRunner = {
    lastCode: null,
    lastStdin: undefined,
    init(): Promise<void> {
      return Promise.resolve();
    },
    run(code: string, stdin = ''): Promise<PyodideRunResult> {
      runner.lastCode = code;
      runner.lastStdin = stdin;
      return Promise.resolve(impl(code, stdin));
    },
    dispose(): void {},
  };
  return runner;
}

function throwingRunner(error: Error): PyodideRunner {
  return {
    init(): Promise<void> {
      return Promise.resolve();
    },
    run(): Promise<PyodideRunResult> {
      return Promise.reject(error);
    },
    dispose(): void {},
  };
}

function wrapStdout(payload: unknown, leading = 'pyodide boot noise\n'): string {
  return `${leading}${TRACE_SENTINEL_BEGIN}\n${JSON.stringify(payload)}\n${TRACE_SENTINEL_END}\n`;
}

function ok(stdout: string): PyodideRunResult {
  return { stdout, stderr: '', durationMs: 1, isError: false };
}

describe('runTrace —— 装配（mock Pyodide 适配）', () => {
  it('解析 sentinel JSON 并经 buildTrace 产出 Trace（source 回填）', async () => {
    const payload = {
      records: [
        { kind: 'line', line: 1 },
        { kind: 'output', line: 1, text: 'hi' },
      ],
      stdout: 'hi\n',
      truncated: false,
    };
    const runner = fakeRunner(() => ok(wrapStdout(payload)));

    const trace = await runTrace('print("hi")', { runner });

    expect(trace.source).toBe('print("hi")');
    expect(trace.stdout).toBe('hi\n');
    expect(trace.truncated).toBe(false);
    expect(trace.events).toEqual<TraceEvent[]>([
      { step: 0, line: 1, kind: 'line' },
      { step: 1, line: 1, kind: 'output', text: 'hi' },
    ]);
  });

  it('把学生代码 base64 注入 harness，并带步数预算与哨兵', async () => {
    const runner = fakeRunner(() => ok(wrapStdout({ records: [], stdout: '', truncated: false })));

    await runTrace('x = 1', { runner });

    const program = runner.lastCode ?? '';
    expect(program).toContain(TRACE_SENTINEL_BEGIN);
    expect(program).toContain(TRACE_SENTINEL_END);
    expect(program).toContain(String(MAX_TRACE_STEPS));
    expect(program).toContain('sys.settrace');
    expect(program).toContain(toBase64Utf8('x = 1'));
    expect(program).not.toContain('__PYT_');
  });

  it('透传 harness 截断标记', async () => {
    const runner = fakeRunner(() =>
      ok(wrapStdout({ records: [{ kind: 'line', line: 2 }], stdout: '', truncated: true })),
    );
    const trace = await runTrace('while True: pass', { runner });
    expect(trace.truncated).toBe(true);
  });

  it('转发 stdin 给适配层', async () => {
    const runner = fakeRunner(() => ok(wrapStdout({ records: [], stdout: '', truncated: false })));
    await runTrace('print(input())', { runner, stdin: '42\n' });
    expect(runner.lastStdin).toBe('42\n');
  });

  it('输出无 sentinel（harness 崩溃）→ 抛 TraceExecutionError 并带 stderr', async () => {
    const runner = fakeRunner(() => ({
      stdout: 'Traceback (most recent call last): ...',
      stderr: 'RuntimeError: harness boom',
      durationMs: 1,
      isError: true,
    }));

    await expect(runTrace('x', { runner })).rejects.toBeInstanceOf(TraceExecutionError);
    await expect(runTrace('x', { runner })).rejects.toThrow(/harness boom/);
  });

  it('Pyodide 不可用（run 抛错）→ 抛 TraceExecutionError，不静默假装', async () => {
    const runner = throwingRunner(new Error('Pyodide can only run in a browser'));

    await expect(runTrace('x', { runner })).rejects.toBeInstanceOf(TraceExecutionError);
    await expect(runTrace('x', { runner })).rejects.toThrow(/browser/);
  });
});

describe('parseRawPayload —— 顶层结构容错', () => {
  it('非对象 → 空 payload', () => {
    expect(parseRawPayload(null)).toEqual({ records: [], stdout: '', truncated: false });
    expect(parseRawPayload('nope')).toEqual({ records: [], stdout: '', truncated: false });
    expect(parseRawPayload(42)).toEqual({ records: [], stdout: '', truncated: false });
  });

  it('缺字段 → 安全默认值', () => {
    expect(parseRawPayload({})).toEqual({ records: [], stdout: '', truncated: false });
    expect(parseRawPayload({ records: 'bad', stdout: 5, truncated: 'yes' })).toEqual({
      records: [],
      stdout: '',
      truncated: false,
    });
  });

  it('合法结构透传', () => {
    const input = { records: [{ kind: 'line', line: 1 }], stdout: 'out', truncated: true };
    expect(parseRawPayload(input)).toEqual(input);
  });
});
