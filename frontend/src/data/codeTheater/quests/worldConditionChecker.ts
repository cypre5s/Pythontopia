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
  WorldCheckResult,
  WorldGoal,
} from './types';

/**
 * 把仅含数字的整数 repr 字符串解析成目标编号；非整数（如变量名、浮点、空）返回 null。
 * Trace 里实参是已 repr 的字符串（契约 types.ts CallEvent.args），故 `water(3)` → "3"。
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
 * （傀儡真的走过去浇了水），而循环计数/打印只回显计数器、并不等于「这块地被浇到」。
 * 形如 `water(plot)` 会被 trace-engine 记成 `CallEvent{funcName:'water', args:['3']}`。
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

function formatPlots(noun: string, plots: readonly number[]): string {
  return plots.map((plot) => `第 ${plot} 块${noun}`).join('、');
}

/** 拼面向学生的可见反馈：明示世界现状（达成/哪几块还干着/浇错到哪），不暴露隐藏断言。 */
function buildCoverageFeedback(
  goal: CoverageWorldGoal,
  parts: { achieved: boolean; unmet: readonly number[]; outOfRange: readonly number[] },
): string {
  const { targetNoun: noun, targets } = goal;
  const wasted =
    parts.outOfRange.length > 0
      ? `另外，你还往不存在的地块（${formatPlots(noun, parts.outOfRange)}）浇了水。`
      : '';

  if (parts.achieved) {
    const ok = `${targets.length} 块${noun}全部浇透了，禾苗缓过来了。`;
    return wasted ? `${ok}${wasted}` : ok;
  }

  const dry = `还有 ${parts.unmet.length} 块${noun}是干的：${formatPlots(noun, parts.unmet)}。`;
  return wasted ? `${dry}${wasted}` : dry;
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

  const achieved = unmet.length === 0;
  const feedback = buildCoverageFeedback(goal, { achieved, unmet, outOfRange });

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
    default:
      return assertNever(goal.kind);
  }
}
