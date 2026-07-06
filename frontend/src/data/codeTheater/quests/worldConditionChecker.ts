/**
 * 世界条件检查器（纯函数，C-quest-sample 的可测核心）。
 *
 * 职责：吃一条学生代码产生的 `Trace`（契约 §5.2），推断学生在世界里
 * 「作用到了哪些目标」，再判定 `WorldGoal` 是否达成、给出可见反馈。
 *
 * 红线遵循：
 * - 纯函数、无副作用、可独立 fixture 单测。
 * - 这是「学习预览」用途；正式掌握度仍走服务端 submission 判题，
 *   本检查器**不**充当判题（Pyodide 预览 ≠ 判题）。
 */

import type { Trace } from '@/services/codeTheater/types';
import type {
  ActedTargetExtractor,
  CodeTheaterQuest,
  CoverageWorldGoal,
  SequenceWorldGoal,
  WorldCheckResult,
  WorldGoal,
} from './types';

/**
 * 把仅含数字的整数 repr 字符串解析成目标编号；非整数（如变量名、浮点、空）返回 null。
 * Trace 里实参是已 repr 的字符串（契约 types.ts CallEvent.args），故 `light(3)` → "3"。
 */
function parseTargetId(raw: string): number | null {
  const trimmed = raw.trim();
  if (!/^-?\d+$/.test(trimmed)) return null;
  const value = Number.parseInt(trimmed, 10);
  return Number.isSafeInteger(value) ? value : null;
}

/**
 * 默认提取策略：把「调用世界动作」视为对某目标生效的证据。
 *
 * 选 `call` 事件而非 `loop_iter`/`output`，是因为它直接表达了**对世界的作用**
 * （傀儡真的走过去点亮了灯），而循环计数/打印只回显计数器、并不等于「这个目标被作用到」。
 * 形如 `light(lamp)` 会被 trace-engine 记成 `CallEvent{funcName:'light', args:['3']}`；
 * 因此世界动作必须在 starterCode 里 `def` 出来（见 types.ts CoverageWorldGoal.actionName）。
 *
 * 返回值保留发生顺序与重复（去重交由判定阶段），便于上层做演出对齐。
 */
export const extractActedTargets: ActedTargetExtractor = (trace: Trace, actionName: string): number[] => {
  const acted: number[] = [];
  for (const event of trace.events) {
    if (event.kind !== 'call' || event.funcName !== actionName) continue;
    const raw = event.args[0];
    if (raw === undefined) continue;
    const target = parseTargetId(raw);
    if (target === null) continue;
    acted.push(target);
  }
  return acted;
};

function formatTargets(unit: string, noun: string, targets: readonly number[]): string {
  return targets.map((target) => `第 ${target} ${unit}${noun}`).join('、');
}

/** 拼面向学生的可见反馈：明示世界现状（达成 / 还差哪几个 / 作用错到哪），不暴露隐藏断言。 */
function buildCoverageFeedback(
  goal: CoverageWorldGoal,
  parts: { achieved: boolean; unmet: readonly number[]; outOfRange: readonly number[] },
): string {
  const noun = goal.targetNoun;
  const unit = goal.unit ?? '个';
  const verb = goal.actionVerb ?? goal.actionName;
  const wasted =
    parts.outOfRange.length > 0
      ? `你还${verb}了不该${verb}的${noun}（${formatTargets(unit, noun, parts.outOfRange)}）。`
      : '';

  if (parts.achieved) {
    const ok = `${goal.targets.length} ${unit}${noun}全部${verb}了。`;
    return wasted ? `${ok}另外，${wasted}` : ok;
  }

  if (parts.unmet.length > 0) {
    const left = `还有 ${parts.unmet.length} ${unit}${noun}没${verb}：${formatTargets(unit, noun, parts.unmet)}。`;
    return wasted ? `${left}另外，${wasted}` : left;
  }

  // 目标都作用到了，却因多碰了不该碰的（forbidExtra）而未达成。
  return `目标${noun}都${verb}了，但${wasted}`;
}

/**
 * 判定覆盖型世界目标是否达成。纯函数。
 * @param goal 覆盖目标（targets / actionName / targetNoun）。
 * @param actedTargets 已作用到的编号（可含重复、可越界），通常来自 {@link extractActedTargets}。
 */
export function checkCoverageGoal(
  goal: CoverageWorldGoal,
  actedTargets: readonly number[],
): WorldCheckResult {
  const targetSet = new Set<number>(goal.targets);
  const actedSet = new Set<number>(actedTargets);

  const satisfied = goal.targets.filter((target) => actedSet.has(target));
  const unmet = goal.targets.filter((target) => !actedSet.has(target));
  const outOfRange = [...actedSet]
    .filter((acted) => !targetSet.has(acted))
    .sort((a, b) => a - b);

  const achieved = unmet.length === 0 && (goal.forbidExtra !== true || outOfRange.length === 0);
  const feedback = buildCoverageFeedback(goal, { achieved, unmet, outOfRange });

  return { achieved, satisfied, unmet, outOfRange, feedback };
}

/** 拼顺序型目标的可见反馈：明示「按序做到第几步 / 在哪一步乱了 / 该做谁却做了谁」。 */
function buildSequenceFeedback(
  goal: SequenceWorldGoal,
  parts: {
    achieved: boolean;
    matched: number;
    acted: readonly number[];
    outOfRange: readonly number[];
  },
): string {
  const noun = goal.targetNoun;
  const unit = goal.unit ?? '个';
  const verb = goal.actionVerb ?? goal.actionName;
  const total = goal.targets.length;
  const one = (target: number): string => `第 ${target} ${unit}${noun}`;
  const wasted =
    parts.outOfRange.length > 0
      ? `另外，你还${verb}了不在顺序名单上的${noun}（${formatTargets(unit, noun, parts.outOfRange)}）。`
      : '';

  if (parts.achieved) {
    return `${total} ${unit}${noun}按规定顺序全部${verb}了。`;
  }

  // 顺序前缀全对，却因为「多做了」（重复 / 收尾多余）而未达成。
  if (parts.matched >= total) {
    const overshoot = `${total} ${unit}${noun}的顺序都对，但你一共${verb}了 ${parts.acted.length} 次、比规定多，留意别重复${verb}。`;
    return wasted ? `${overshoot}${wasted}` : overshoot;
  }

  const expected = goal.targets[parts.matched];
  // matched < total 时 expected 必存在；用兜底空串满足 strict 索引检查，正常路径不触发。
  const expectedText = expected === undefined ? '' : one(expected);
  const actual = parts.acted[parts.matched];

  if (actual === undefined) {
    // 中途停下：前 matched 步按序完成，后面没做。
    const head =
      parts.matched === 0
        ? `还没开始按顺序${verb}：第一步本该${verb}${expectedText}。`
        : `按顺序${verb}到第 ${parts.matched} 步就停了，接下来本该${verb}${expectedText}。`;
    return wasted ? `${head}${wasted}` : head;
  }

  // 顺序在第 matched+1 步乱了：本该 expected，却先做了 actual。
  const mixed = `顺序在第 ${parts.matched + 1} 步乱了：本该${verb}${expectedText}，你却先${verb}了第 ${actual} ${unit}${noun}。`;
  return wasted ? `${mixed}${wasted}` : mixed;
}

/**
 * 判定顺序覆盖型世界目标是否达成。纯函数。
 *
 * 逐位比对学生「实际作用顺序」`actedTargets` 与「规定顺序」`goal.targets`：第一处不一致
 * 即为断点，断点之前算按序达成、之后算未达成。达成要求「不多不少、每步都对」（含不得多作用）。
 *
 * @param goal 顺序目标（targets 的数组顺序即规定先后）。
 * @param actedTargets 已作用到的编号（**保留发生顺序**，可含重复 / 越界），通常来自 {@link extractActedTargets}。
 */
export function checkSequenceGoal(
  goal: SequenceWorldGoal,
  actedTargets: readonly number[],
): WorldCheckResult {
  const targetSet = new Set<number>(goal.targets);

  let matched = 0;
  while (matched < goal.targets.length && actedTargets[matched] === goal.targets[matched]) {
    matched += 1;
  }

  const satisfied = goal.targets.slice(0, matched);
  const unmet = goal.targets.slice(matched);
  const outOfRange = [...new Set<number>(actedTargets)]
    .filter((acted) => !targetSet.has(acted))
    .sort((a, b) => a - b);

  // 达成 = 实际顺序与规定顺序逐位完全一致，且不多不少。
  const achieved = matched === goal.targets.length && actedTargets.length === goal.targets.length;
  const feedback = buildSequenceFeedback(goal, { achieved, matched, acted: actedTargets, outOfRange });

  return { achieved, satisfied, unmet, outOfRange, feedback };
}

/** 穷尽性兜底：新增 WorldGoalKind 却忘了处理时，编译期报错 + 运行期 fail fast。 */
function assertNever(value: never): never {
  throw new Error(`未处理的世界目标种类: ${JSON.stringify(value)}`);
}

/**
 * 顶层入口：给定任务与学生 Trace，判定其世界目标是否达成。
 * @param extract 可选的「被作用目标」提取策略；默认按 `call` 事件
 *   （Phase-2 若 trace-engine 用别的事件表达动作，可在此注入替代策略）。
 */
export function checkQuestWorldGoal(
  quest: CodeTheaterQuest,
  trace: Trace,
  extract: ActedTargetExtractor = extractActedTargets,
): WorldCheckResult {
  const goal: WorldGoal = quest.worldGoal;
  switch (goal.kind) {
    case 'coverage':
      return checkCoverageGoal(goal, extract(trace, goal.actionName));
    case 'sequence':
      return checkSequenceGoal(goal, extract(trace, goal.actionName));
    default:
      return assertNever(goal);
  }
}
