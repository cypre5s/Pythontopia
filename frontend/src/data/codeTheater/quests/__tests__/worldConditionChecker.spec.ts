import { describe, it, expect } from 'vitest';
import type { CallEvent, LoopIterEvent, OutputEvent, Trace, TraceEvent } from '@/services/codeTheater/types';
import { WATER_DRY_FIELDS_QUEST } from '../waterDryFields';
import {
  checkCoverageGoal,
  checkQuestWorldGoal,
  extractActedTargets,
} from '../worldConditionChecker';
import { toWorldEffectCommands } from '../worldEffects';
import type { CoverageWorldGoal } from '../types';

const goal = WATER_DRY_FIELDS_QUEST.worldGoal as CoverageWorldGoal;

function call(step: number, funcName: string, arg: string): CallEvent {
  return { step, line: 5, kind: 'call', funcName, args: [arg], depth: 1 };
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

function output(step: number, text: string): OutputEvent {
  return { step, line: 5, kind: 'output', text };
}

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

describe('extractActedTargets', () => {
  it('只认指定动作名的调用，并解析整数实参', () => {
    const trace = traceOf([
      call(0, 'water', '1'),
      call(1, 'print', '999'), // 别的函数：忽略
      call(2, 'water', 'plot'), // 非整数实参：忽略
      call(3, 'water', '2'),
      output(4, '浇好了'), // 输出事件：不计
    ]);
    expect(extractActedTargets(trace, 'water')).toEqual([1, 2]);
  });

  it('保留发生顺序与重复（去重交给判定阶段）', () => {
    expect(extractActedTargets(wateringTrace([1, 1, 2]), 'water')).toEqual([1, 1, 2]);
  });
});

describe('checkQuestWorldGoal · 浇灌所有旱地', () => {
  it('达成：range(1, 5) 浇遍第 1~4 块旱地', () => {
    const result = checkQuestWorldGoal(WATER_DRY_FIELDS_QUEST, wateringTrace([1, 2, 3, 4]));
    expect(result.achieved).toBe(true);
    expect(result.satisfied).toEqual([1, 2, 3, 4]);
    expect(result.unmet).toEqual([]);
    expect(result.outOfRange).toEqual([]);
    expect(result.feedback).toContain('全部浇透');
  });

  it('off-by-one 未达成：range(1, 4) 漏掉第 4 块（左闭右开）', () => {
    const result = checkQuestWorldGoal(WATER_DRY_FIELDS_QUEST, wateringTrace([1, 2, 3]));
    expect(result.achieved).toBe(false);
    expect(result.satisfied).toEqual([1, 2, 3]);
    expect(result.unmet).toEqual([4]);
    expect(result.outOfRange).toEqual([]);
    expect(result.feedback).toContain('第 4 块旱地');
    expect(result.feedback).toContain('是干的');
  });

  it('0 基 off-by-one：range(4) 浇到不存在的第 0 块、漏掉第 4 块', () => {
    const result = checkQuestWorldGoal(WATER_DRY_FIELDS_QUEST, wateringTrace([0, 1, 2, 3]));
    expect(result.achieved).toBe(false);
    expect(result.unmet).toEqual([4]);
    expect(result.outOfRange).toEqual([0]);
    expect(result.feedback).toContain('不存在的地块');
  });

  it('空轨迹：一块都没浇', () => {
    const result = checkQuestWorldGoal(WATER_DRY_FIELDS_QUEST, traceOf([]));
    expect(result.achieved).toBe(false);
    expect(result.unmet).toEqual([1, 2, 3, 4]);
  });

  it('重复浇同一块仍按集合判定（达成）', () => {
    const result = checkQuestWorldGoal(WATER_DRY_FIELDS_QUEST, wateringTrace([1, 1, 2, 3, 4, 4]));
    expect(result.achieved).toBe(true);
    expect(result.satisfied).toEqual([1, 2, 3, 4]);
  });
});

describe('checkCoverageGoal · 纯抽象世界状态', () => {
  it('可直接吃「已作用编号」而不经过 Trace', () => {
    const custom: CoverageWorldGoal = {
      kind: 'coverage',
      targets: [1, 2, 3],
      actionName: 'light',
      targetNoun: '路灯',
    };
    expect(checkCoverageGoal(custom, [1, 2, 3]).achieved).toBe(true);
    const partial = checkCoverageGoal(custom, [1, 3]);
    expect(partial.achieved).toBe(false);
    expect(partial.unmet).toEqual([2]);
    expect(partial.feedback).toContain('路灯');
  });
});

describe('toWorldEffectCommands', () => {
  it('off-by-one 结果 → 第 4 块为 dry、其余 watered，全部 world_effect', () => {
    const result = checkCoverageGoal(goal, [1, 2, 3]);
    const commands = toWorldEffectCommands(goal, result);
    expect(commands).toHaveLength(4);
    expect(commands.every((c) => c.action === 'world_effect')).toBe(true);
    expect(commands.map((c) => c.payload.state)).toEqual(['watered', 'watered', 'watered', 'dry']);
    expect(commands.map((c) => c.payload.target)).toEqual([1, 2, 3, 4]);
    expect(commands[0]?.payload.targetNoun).toBe('旱地');
  });

  it('step 与时长可对齐 Trace 末尾', () => {
    const result = checkCoverageGoal(goal, [1, 2, 3, 4]);
    const commands = toWorldEffectCommands(goal, result, { startStep: 10, durationMs: 250 });
    expect(commands.map((c) => c.step)).toEqual([10, 11, 12, 13]);
    expect(commands.every((c) => c.durationMs === 250)).toBe(true);
    expect(commands.every((c) => c.payload.state === 'watered')).toBe(true);
  });
});
