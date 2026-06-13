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
  traceToRenderCommands,
} from '@/services/codeTheater/pipeline';
import { WATER_DRY_FIELDS_QUEST } from '@/data/codeTheater/quests';

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
    line: 4,
    kind: 'loop_iter',
    loopId: 'for@4',
    loopType: 'for',
    iter,
    varName: 'plot',
    value: String(value),
  };
}
function call(step: number, funcName: string, arg: string): CallEvent {
  return { step, line: 5, kind: 'call', funcName, args: [arg], depth: 1 };
}
function output(step: number, text: string): OutputEvent {
  return { step, line: 6, kind: 'output', text };
}

/** 模拟 `for plot in range(...)：water(plot)` 真实留下的轨迹（loop_iter 与 call 交替）。 */
function wateringTrace(plots: readonly number[]): Trace {
  const events: TraceEvent[] = [];
  let step = 0;
  plots.forEach((plot, iter) => {
    events.push(loopIter(step++, iter, plot));
    events.push(call(step++, 'water', String(plot)));
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

describe('buildQuestResult · trace + 浇灌任务 → 世界判定 + world_effect', () => {
  it('达成：浇遍第 1~4 块 → world.achieved，末尾追加 4 条全 watered 的 world_effect', () => {
    const trace = wateringTrace([1, 2, 3, 4]);
    const result = buildQuestResult(trace, WATER_DRY_FIELDS_QUEST);

    expect(result.world.achieved).toBe(true);
    expect(result.world.satisfied).toEqual([1, 2, 3, 4]);

    const effects = result.commands.filter((c) => c.action === 'world_effect');
    expect(effects).toHaveLength(4);
    expect(effects.every((c) => c.payload.state === 'watered')).toBe(true);
    // world_effect 接在 Trace 末尾（step 从 stepCount 起）。
    expect(effects.map((c) => c.step)).toEqual([
      trace.stepCount,
      trace.stepCount + 1,
      trace.stepCount + 2,
      trace.stepCount + 3,
    ]);
  });

  it('off-by-one：range(1,4) 漏掉第 4 块 → 第 4 块 world_effect 为 dry', () => {
    const result = buildQuestResult(wateringTrace([1, 2, 3]), WATER_DRY_FIELDS_QUEST);

    expect(result.world.achieved).toBe(false);
    expect(result.world.unmet).toEqual([4]);

    const effects = result.commands.filter((c) => c.action === 'world_effect');
    expect(effects.map((c) => c.payload.state)).toEqual([
      'watered',
      'watered',
      'watered',
      'dry',
    ]);
    expect(effects.map((c) => c.payload.target)).toEqual([1, 2, 3, 4]);
  });
});
