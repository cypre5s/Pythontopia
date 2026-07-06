import { describe, it, expect } from 'vitest';
import type {
  AssignEvent,
  CallEvent,
  LoopIterEvent,
  OutputEvent,
  Trace,
  TraceEvent,
} from '@/services/codeTheater/types';
import {
  buildQuestResult,
  runFreeVisualization,
  traceToRenderCommands,
} from '@/services/codeTheater/pipeline';
import { TRACE_SENTINEL_BEGIN, TRACE_SENTINEL_END } from '@/services/codeTheater/trace';
import type { PyodideRunResult, PyodideRunner } from '@game/adapters/pyodide';
import { LIGHT_STREET_LAMPS_QUEST } from '@/data/codeTheater/quests';

/**
 * 代码剧场端到端集成门（MVP gate）：用真实 C-mappers + C-quest 模块，验证
 * trace → RenderCommand[] → world_effect 整条数据流贯通（不依赖 Pyodide）。
 */

function traceOf(events: TraceEvent[]): Trace {
  return {
    version: 1,
    source: '# fixture',
    events,
    stdout: '',
    truncated: false,
    stepCount: events.length,
  };
}

function assign(step: number, varName: string, value: string): AssignEvent {
  return { step, line: 1, kind: 'assign', varName, value, scope: 'global' };
}
function loopIter(step: number, iter: number, value: number): LoopIterEvent {
  return {
    step,
    line: 7,
    kind: 'loop_iter',
    loopId: 'for@7',
    loopType: 'for',
    iter,
    varName: 'lamp',
    value: String(value),
  };
}
function call(step: number, funcName: string, arg: string): CallEvent {
  return { step, line: 8, kind: 'call', funcName, args: [arg], depth: 1 };
}
function output(step: number, text: string): OutputEvent {
  return { step, line: 9, kind: 'output', text };
}

/** 模拟 `for lamp in range(...)：light(lamp)` 真实留下的轨迹（loop_iter 与 call 交替）。 */
function lightingTrace(lamps: readonly number[]): Trace {
  const events: TraceEvent[] = [];
  let step = 0;
  lamps.forEach((lamp, iter) => {
    events.push(loopIter(step++, iter, lamp));
    events.push(call(step++, 'light', String(lamp)));
  });
  return traceOf(events);
}

describe('traceToRenderCommands · 引擎累积 MapContext + 真实 mapper', () => {
  it('每条指令的 step 都来自某个轨迹事件，且映射出对应动作', () => {
    const trace = traceOf([
      assign(0, 'total', '0'),
      loopIter(1, 0, 1),
      output(2, 'hi'),
    ]);
    const commands = traceToRenderCommands(trace);

    expect(commands.length).toBeGreaterThanOrEqual(3);
    const eventSteps = new Set(trace.events.map((e) => e.step));
    expect(commands.every((c) => eventSteps.has(c.step))).toBe(true);
    const actions = new Set(commands.map((c) => c.action));
    expect(actions.has('set_var')).toBe(true); // assign
    expect(actions.has('walk_loop')).toBe(true); // loop_iter
    expect(actions.has('speak')).toBe(true); // output
  });

  it('累积变量值：同名变量重赋值时，第二次的 ctx 能看到旧值（previousValue）', () => {
    const trace = traceOf([assign(0, 'x', '1'), assign(1, 'x', '2')]);
    const commands = traceToRenderCommands(trace);
    const secondSetVar = commands.find(
      (c) => c.step === 1 && c.action === 'set_var',
    );
    expect(secondSetVar?.payload.previousValue).toBe('1');
    expect(secondSetVar?.payload.value).toBe('2');
  });

  it('defaultDurationMs 可覆盖', () => {
    const trace = traceOf([assign(0, 'a', '5')]);
    const commands = traceToRenderCommands(trace, { defaultDurationMs: 999 });
    expect(commands.every((c) => c.durationMs > 0)).toBe(true);
  });
});

describe('buildQuestResult · trace + 点亮任务 → 世界判定 + world_effect', () => {
  it('达成：点遍第 1~4 盏 → world.achieved，末尾追加 4 条全 done 的 world_effect', () => {
    const trace = lightingTrace([1, 2, 3, 4]);
    const result = buildQuestResult(trace, LIGHT_STREET_LAMPS_QUEST);

    expect(result.world.achieved).toBe(true);
    expect(result.world.satisfied).toEqual([1, 2, 3, 4]);

    const effects = result.commands.filter((c) => c.action === 'world_effect');
    expect(effects).toHaveLength(4);
    expect(effects.every((c) => c.payload.state === 'done')).toBe(true);
    // world_effect 接在 Trace 末尾（step 从 stepCount 起）。
    expect(effects.map((c) => c.step)).toEqual([
      trace.stepCount,
      trace.stepCount + 1,
      trace.stepCount + 2,
      trace.stepCount + 3,
    ]);
  });

  it('off-by-one：range(1,4) 漏掉第 4 盏 → 第 4 盏 world_effect 为 pending', () => {
    const result = buildQuestResult(lightingTrace([1, 2, 3]), LIGHT_STREET_LAMPS_QUEST);

    expect(result.world.achieved).toBe(false);
    expect(result.world.unmet).toEqual([4]);

    const effects = result.commands.filter((c) => c.action === 'world_effect');
    expect(effects.map((c) => c.payload.state)).toEqual([
      'done',
      'done',
      'done',
      'pending',
    ]);
    expect(effects.map((c) => c.payload.target)).toEqual([1, 2, 3, 4]);
  });
});

// ── 自由可视化（训练场 WA/RE → 看自己的代码怎么跑），mock Pyodide 适配 ──
interface FreeFakeRunner extends PyodideRunner {
  lastStdin: string | undefined;
}
function freeFakeRunner(stdout: string): FreeFakeRunner {
  const runner: FreeFakeRunner = {
    lastStdin: undefined,
    init: () => Promise.resolve(),
    run: (_code: string, stdin = ''): Promise<PyodideRunResult> => {
      runner.lastStdin = stdin;
      return Promise.resolve({ stdout, stderr: '', durationMs: 1, isError: false });
    },
    dispose: () => {},
  };
  return runner;
}
function wrapTraceStdout(payload: unknown): string {
  return `boot\n${TRACE_SENTINEL_BEGIN}\n${JSON.stringify(payload)}\n${TRACE_SENTINEL_END}\n`;
}

describe('runFreeVisualization · 自由可视化（无世界目标，mock Pyodide）', () => {
  it('任意代码 → trace + 渲染指令；stdin 透传；不产出 world_effect', async () => {
    const payload = {
      records: [
        { kind: 'line', line: 1 },
        { kind: 'assign', line: 1, varName: 'n', value: '3', scope: 'global' },
        { kind: 'output', line: 2, text: '3' },
      ],
      stdout: '3\n',
      truncated: false,
    };
    const runner = freeFakeRunner(wrapTraceStdout(payload));
    const res = await runFreeVisualization('n = int(input())\nprint(n)', '3\n', { runner });

    expect(runner.lastStdin).toBe('3\n');
    expect(res.trace.events.length).toBeGreaterThanOrEqual(3);
    expect(res.commands.length).toBeGreaterThan(0);
    expect(res.commands.some((c) => c.action === 'world_effect')).toBe(false);
    const actions = new Set(res.commands.map((c) => c.action));
    expect(actions.has('set_var')).toBe(true);
    expect(actions.has('speak')).toBe(true);
  });

  it('运行时异常也能可视化（exception → stumble），不抛错', async () => {
    const payload = {
      records: [
        { kind: 'line', line: 1 },
        { kind: 'exception', line: 1, excType: 'ZeroDivisionError', message: 'division by zero' },
      ],
      stdout: '',
      truncated: false,
    };
    const runner = freeFakeRunner(wrapTraceStdout(payload));
    const res = await runFreeVisualization('print(1 / 0)', '', { runner });
    expect(res.commands.some((c) => c.action === 'stumble')).toBe(true);
  });
});
