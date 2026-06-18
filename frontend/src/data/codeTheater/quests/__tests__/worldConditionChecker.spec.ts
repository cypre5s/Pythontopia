import { describe, it, expect } from 'vitest';
import type { CallEvent, LoopIterEvent, OutputEvent, Trace, TraceEvent } from '@/services/codeTheater/types';
import { LIGHT_STREET_LAMPS_QUEST } from '../lightStreetLamps';
import { OPEN_EVEN_GATES_QUEST } from '../openEvenGates';
import { CLEAN_CINEMA_SEATS_QUEST } from '../cleanCinemaSeats';
import { REKEY_ODD_LOCKERS_QUEST } from '../rekeyOddLockers';
import { SHELVE_RETURNED_BOOKS_QUEST } from '../shelveReturnedBooks';
import { LIGHT_BEACON_CHAIN_QUEST } from '../lightBeaconChain';
import {
  checkCoverageGoal,
  checkQuestWorldGoal,
  checkSequenceGoal,
  extractActedTargets,
} from '../worldConditionChecker';
import { toWorldEffectCommands } from '../worldEffects';
import type { CoverageWorldGoal, SequenceWorldGoal } from '../types';

const lampGoal = LIGHT_STREET_LAMPS_QUEST.worldGoal as CoverageWorldGoal;

function call(step: number, funcName: string, arg: string): CallEvent {
  return { step, line: 8, kind: 'call', funcName, args: [arg], depth: 1 };
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

function output(step: number, text: string): OutputEvent {
  return { step, line: 8, kind: 'output', text };
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

describe('extractActedTargets', () => {
  it('只认指定动作名的调用，并解析整数实参', () => {
    const trace = traceOf([
      call(0, 'light', '1'),
      call(1, 'print', '999'), // 别的函数：忽略
      call(2, 'light', 'lamp'), // 非整数实参：忽略
      call(3, 'light', '2'),
      output(4, '点好了'), // 输出事件：不计
    ]);
    expect(extractActedTargets(trace, 'light')).toEqual([1, 2]);
  });

  it('保留发生顺序与重复（去重交给判定阶段）', () => {
    expect(extractActedTargets(lightingTrace([1, 1, 2]), 'light')).toEqual([1, 1, 2]);
  });
});

describe('checkQuestWorldGoal · 点亮整条街的路灯', () => {
  it('达成：range(1, 5) 点遍第 1~4 盏路灯', () => {
    const result = checkQuestWorldGoal(LIGHT_STREET_LAMPS_QUEST, lightingTrace([1, 2, 3, 4]));
    expect(result.achieved).toBe(true);
    expect(result.satisfied).toEqual([1, 2, 3, 4]);
    expect(result.unmet).toEqual([]);
    expect(result.outOfRange).toEqual([]);
    expect(result.feedback).toContain('全部点亮');
  });

  it('off-by-one 未达成：range(1, 4) 漏掉第 4 盏（左闭右开）', () => {
    const result = checkQuestWorldGoal(LIGHT_STREET_LAMPS_QUEST, lightingTrace([1, 2, 3]));
    expect(result.achieved).toBe(false);
    expect(result.satisfied).toEqual([1, 2, 3]);
    expect(result.unmet).toEqual([4]);
    expect(result.outOfRange).toEqual([]);
    expect(result.feedback).toContain('第 4 盏路灯');
    expect(result.feedback).toContain('没点亮');
  });

  it('0 基 off-by-one：range(4) 点到不该点的第 0 盏、漏掉第 4 盏', () => {
    const result = checkQuestWorldGoal(LIGHT_STREET_LAMPS_QUEST, lightingTrace([0, 1, 2, 3]));
    expect(result.achieved).toBe(false);
    expect(result.unmet).toEqual([4]);
    expect(result.outOfRange).toEqual([0]);
    expect(result.feedback).toContain('不该点亮');
  });

  it('空轨迹：一盏都没点', () => {
    const result = checkQuestWorldGoal(LIGHT_STREET_LAMPS_QUEST, traceOf([]));
    expect(result.achieved).toBe(false);
    expect(result.unmet).toEqual([1, 2, 3, 4]);
  });

  it('重复点同一盏仍按集合判定（达成）', () => {
    const result = checkQuestWorldGoal(LIGHT_STREET_LAMPS_QUEST, lightingTrace([1, 1, 2, 3, 4, 4]));
    expect(result.achieved).toBe(true);
    expect(result.satisfied).toEqual([1, 2, 3, 4]);
  });
});

describe('checkQuestWorldGoal · 只开双数号城门（forbidExtra=true）', () => {
  function unlockTrace(gates: readonly number[]): Trace {
    return traceOf(gates.map((gate, i) => call(i, 'unlock', String(gate))));
  }

  it('达成：只开第 2、4、6 座', () => {
    const result = checkQuestWorldGoal(OPEN_EVEN_GATES_QUEST, unlockTrace([2, 4, 6]));
    expect(result.achieved).toBe(true);
    expect(result.satisfied).toEqual([2, 4, 6]);
    expect(result.outOfRange).toEqual([]);
    expect(result.feedback).toContain('全部打开');
  });

  it('开了单数号陷阱门 → 即便双数号全开也判未达成', () => {
    const result = checkQuestWorldGoal(OPEN_EVEN_GATES_QUEST, unlockTrace([1, 2, 3, 4, 5, 6]));
    expect(result.achieved).toBe(false);
    expect(result.satisfied).toEqual([2, 4, 6]);
    expect(result.unmet).toEqual([]);
    expect(result.outOfRange).toEqual([1, 3, 5]);
    expect(result.feedback).toContain('不该打开');
  });

  it('漏开双数号 → 未达成', () => {
    const result = checkQuestWorldGoal(OPEN_EVEN_GATES_QUEST, unlockTrace([2, 4]));
    expect(result.achieved).toBe(false);
    expect(result.unmet).toEqual([6]);
  });
});

describe('checkCoverageGoal · 纯抽象世界状态', () => {
  it('可直接吃「已作用编号」而不经过 Trace', () => {
    const custom: CoverageWorldGoal = {
      kind: 'coverage',
      targets: [1, 2, 3],
      actionName: 'light',
      actionVerb: '点亮',
      targetNoun: '路灯',
      unit: '盏',
    };
    expect(checkCoverageGoal(custom, [1, 2, 3]).achieved).toBe(true);
    const partial = checkCoverageGoal(custom, [1, 3]);
    expect(partial.achieved).toBe(false);
    expect(partial.unmet).toEqual([2]);
    expect(partial.feedback).toContain('路灯');
  });

  it('actionVerb/unit 缺省时回退（动词回退 actionName、量词回退「个」）', () => {
    const bare: CoverageWorldGoal = {
      kind: 'coverage',
      targets: [1, 2],
      actionName: 'tap',
      targetNoun: '开关',
    };
    const result = checkCoverageGoal(bare, [1]);
    expect(result.feedback).toContain('个开关');
    expect(result.feedback).toContain('tap');
  });
});

describe('toWorldEffectCommands', () => {
  it('off-by-one 结果 → 第 4 盏为 pending、其余 done，全部 world_effect', () => {
    const result = checkCoverageGoal(lampGoal, [1, 2, 3]);
    const commands = toWorldEffectCommands(lampGoal, result);
    expect(commands).toHaveLength(4);
    expect(commands.every((c) => c.action === 'world_effect')).toBe(true);
    expect(commands.map((c) => c.payload.state)).toEqual(['done', 'done', 'done', 'pending']);
    expect(commands.map((c) => c.payload.target)).toEqual([1, 2, 3, 4]);
    expect(commands[0]?.payload.targetNoun).toBe('路灯');
  });

  it('step 与时长可对齐 Trace 末尾', () => {
    const result = checkCoverageGoal(lampGoal, [1, 2, 3, 4]);
    const commands = toWorldEffectCommands(lampGoal, result, { startStep: 10, durationMs: 250 });
    expect(commands.map((c) => c.step)).toEqual([10, 11, 12, 13]);
    expect(commands.every((c) => c.durationMs === 250)).toBe(true);
    expect(commands.every((c) => c.payload.state === 'done')).toBe(true);
  });
});

/** 按发生顺序把「对 actionName 的一串调用」拼成 Trace（实参即目标编号，保留顺序与重复）。 */
function actTrace(actionName: string, targets: readonly number[]): Trace {
  return traceOf(targets.map((target, i) => call(i, actionName, String(target))));
}

describe('checkQuestWorldGoal · 新增 coverage 任务', () => {
  it('打扫放映厅座位（嵌套循环）：6 个座位全打扫 → 达成', () => {
    const result = checkQuestWorldGoal(CLEAN_CINEMA_SEATS_QUEST, actTrace('clean', [1, 2, 3, 4, 5, 6]));
    expect(result.achieved).toBe(true);
    expect(result.feedback).toContain('全部打扫');
  });

  it('打扫放映厅座位：内层 range 少一列 → 每排都漏最后一个（第 3、6）', () => {
    const result = checkQuestWorldGoal(CLEAN_CINEMA_SEATS_QUEST, actTrace('clean', [1, 2, 4, 5]));
    expect(result.achieved).toBe(false);
    expect(result.unmet).toEqual([3, 6]);
    expect(result.feedback).toContain('第 3 个座位');
    expect(result.feedback).toContain('第 6 个座位');
    expect(result.feedback).toContain('没打扫');
  });

  it('单数号储物柜换锁（range 步长）：1,3,5,7,9 全换 → 达成', () => {
    const result = checkQuestWorldGoal(REKEY_ODD_LOCKERS_QUEST, actTrace('relock', [1, 3, 5, 7, 9]));
    expect(result.achieved).toBe(true);
    expect(result.feedback).toContain('全部换锁');
  });

  it('单数号储物柜换锁：起手 range(1,9,2) 漏第 9 号（带步长的末尾 off-by-one）', () => {
    const result = checkQuestWorldGoal(REKEY_ODD_LOCKERS_QUEST, actTrace('relock', [1, 3, 5, 7]));
    expect(result.achieved).toBe(false);
    expect(result.unmet).toEqual([9]);
    expect(result.feedback).toContain('第 9 个储物柜');
  });

  it('单数号储物柜换锁（forbidExtra）：碰了双数号（员工专用）即未达成', () => {
    const result = checkQuestWorldGoal(REKEY_ODD_LOCKERS_QUEST, actTrace('relock', [1, 2, 3, 5, 7, 9]));
    expect(result.achieved).toBe(false);
    expect(result.unmet).toEqual([]);
    expect(result.outOfRange).toEqual([2]);
    expect(result.feedback).toContain('不该换锁');
  });

  it('归还图书（list append）：1~5 全归位 → 达成', () => {
    const result = checkQuestWorldGoal(SHELVE_RETURNED_BOOKS_QUEST, actTrace('shelve', [1, 2, 3, 4, 5]));
    expect(result.achieved).toBe(true);
    expect(result.feedback).toContain('全部归位');
  });

  it('归还图书：起手清单漏 append 第 4 号 → 第 4 本没归位', () => {
    const result = checkQuestWorldGoal(SHELVE_RETURNED_BOOKS_QUEST, actTrace('shelve', [1, 2, 3, 5]));
    expect(result.achieved).toBe(false);
    expect(result.unmet).toEqual([4]);
    expect(result.feedback).toContain('第 4 本图书');
    expect(result.feedback).toContain('没归位');
  });
});

describe('checkSequenceGoal · 纯顺序判定', () => {
  // 规定顺序刻意非升序（3→1→2），凸显「顺序也算数」而非简单覆盖。
  const seqGoal: SequenceWorldGoal = {
    kind: 'sequence',
    targets: [3, 1, 2],
    actionName: 'tap',
    actionVerb: '敲',
    targetNoun: '鼓',
    unit: '面',
  };

  it('完全按规定顺序 → 达成', () => {
    const result = checkSequenceGoal(seqGoal, [3, 1, 2]);
    expect(result.achieved).toBe(true);
    expect(result.satisfied).toEqual([3, 1, 2]);
    expect(result.unmet).toEqual([]);
    expect(result.outOfRange).toEqual([]);
    expect(result.feedback).toContain('按规定顺序全部敲');
  });

  it('第一步就错序 → 未达成，断点在第 1 步', () => {
    const result = checkSequenceGoal(seqGoal, [1, 3, 2]);
    expect(result.achieved).toBe(false);
    expect(result.satisfied).toEqual([]);
    expect(result.unmet).toEqual([3, 1, 2]);
    expect(result.feedback).toContain('第 1 步乱了');
    expect(result.feedback).toContain('本该敲第 3 面鼓');
    expect(result.feedback).toContain('第 1 面鼓');
  });

  it('中途停下：前缀对、后面没做', () => {
    const result = checkSequenceGoal(seqGoal, [3]);
    expect(result.achieved).toBe(false);
    expect(result.satisfied).toEqual([3]);
    expect(result.unmet).toEqual([1, 2]);
    expect(result.feedback).toContain('到第 1 步就停了');
    expect(result.feedback).toContain('本该敲第 1 面鼓');
  });

  it('中途错序：前缀对到第 1 步，第 2 步做错', () => {
    const result = checkSequenceGoal(seqGoal, [3, 2, 1]);
    expect(result.achieved).toBe(false);
    expect(result.satisfied).toEqual([3]);
    expect(result.unmet).toEqual([1, 2]);
    expect(result.feedback).toContain('第 2 步乱了');
    expect(result.feedback).toContain('本该敲第 1 面鼓');
    expect(result.feedback).toContain('先敲了第 2 面鼓');
  });

  it('顺序全对但多做了（重复收尾）→ 未达成', () => {
    const result = checkSequenceGoal(seqGoal, [3, 1, 2, 2]);
    expect(result.achieved).toBe(false);
    expect(result.unmet).toEqual([]);
    expect(result.outOfRange).toEqual([]);
    expect(result.feedback).toContain('顺序都对');
    expect(result.feedback).toContain('比规定多');
  });

  it('顺序全对但敲了名单外的鼓 → 未达成并提示越界', () => {
    const result = checkSequenceGoal(seqGoal, [3, 1, 2, 9]);
    expect(result.achieved).toBe(false);
    expect(result.outOfRange).toEqual([9]);
    expect(result.feedback).toContain('不在顺序名单上');
    expect(result.feedback).toContain('第 9 面鼓');
  });

  it('空轨迹：还没开始按顺序作用', () => {
    const result = checkSequenceGoal(seqGoal, []);
    expect(result.achieved).toBe(false);
    expect(result.satisfied).toEqual([]);
    expect(result.unmet).toEqual([3, 1, 2]);
    expect(result.feedback).toContain('还没开始按顺序敲');
  });
});

describe('checkQuestWorldGoal · 烽火台逆序点燃（sequence）', () => {
  it('达成：从第 5 座倒着点回第 1 座（5→4→3→2→1）', () => {
    const result = checkQuestWorldGoal(LIGHT_BEACON_CHAIN_QUEST, actTrace('signal', [5, 4, 3, 2, 1]));
    expect(result.achieved).toBe(true);
    expect(result.satisfied).toEqual([5, 4, 3, 2, 1]);
    expect(result.unmet).toEqual([]);
    expect(result.feedback).toContain('按规定顺序全部点燃');
  });

  it('起手用 range(1,6) 顺序点反了（1→5）→ 第 1 步就乱', () => {
    const result = checkQuestWorldGoal(LIGHT_BEACON_CHAIN_QUEST, actTrace('signal', [1, 2, 3, 4, 5]));
    expect(result.achieved).toBe(false);
    expect(result.satisfied).toEqual([]);
    expect(result.feedback).toContain('第 1 步乱了');
    expect(result.feedback).toContain('本该点燃第 5 座烽火台');
    expect(result.feedback).toContain('第 1 座烽火台');
  });
});

describe('toWorldEffectCommands · sequence 与 coverage payload 同构', () => {
  const beaconGoal = LIGHT_BEACON_CHAIN_QUEST.worldGoal as SequenceWorldGoal;

  it('sequence：按规定顺序逐目标出 world_effect，done/pending + 四键同构 + 额外 order', () => {
    const result = checkSequenceGoal(beaconGoal, [5, 4]); // 只按序点了前两座
    const commands = toWorldEffectCommands(beaconGoal, result);
    expect(commands).toHaveLength(5);
    expect(commands.every((c) => c.action === 'world_effect')).toBe(true);
    // payload.target 按规定顺序（goal.targets）排布。
    expect(commands.map((c) => c.payload.target)).toEqual([5, 4, 3, 2, 1]);
    expect(commands.map((c) => c.payload.state)).toEqual([
      'done',
      'done',
      'pending',
      'pending',
      'pending',
    ]);
    // sequence 专属 order（1 基），供舞台演出先后。
    expect(commands.map((c) => c.payload.order)).toEqual([1, 2, 3, 4, 5]);
    const first = commands[0];
    expect(first).toBeDefined();
    if (first) {
      expect(first.payload.goal).toBe('sequence');
      expect(first.payload.targetNoun).toBe('烽火台');
      expect(Object.keys(first.payload).sort()).toEqual([
        'goal',
        'order',
        'state',
        'target',
        'targetNoun',
      ]);
    }
  });

  it('coverage 的 payload 保持原四键（不掺入 order），与 sequence 的前四键同构', () => {
    const result = checkCoverageGoal(lampGoal, [1, 2, 3]);
    const commands = toWorldEffectCommands(lampGoal, result);
    const first = commands[0];
    expect(first).toBeDefined();
    if (first) {
      expect(first.payload.goal).toBe('coverage');
      expect(Object.keys(first.payload).sort()).toEqual(['goal', 'state', 'target', 'targetNoun']);
    }
  });
});
