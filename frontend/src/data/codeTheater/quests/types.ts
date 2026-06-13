/**
 * 代码剧场「具身任务」自有类型（C-quest-sample 独占）。
 *
 * 设计要点（见并发契约 §5 与本任务红线）：
 * - 这些类型**只属于 quests/ 目录**，不写进共享 `services/codeTheater/types.ts`。
 * - 任务用「世界条件（WorldCondition / WorldGoal）」替代隐藏断言：达成与否由
 *   学生代码在世界里**留下的可见痕迹**判定，反馈也是可见的世界现状
 *   （如「第 4 块旱地还干着」），而非偷偷比对期望输出。
 * - 世界条件检查是纯逻辑：吃一条 `Trace`（契约 §5.2）或抽象世界状态，
 *   产出可独立单测的结构化结果。
 */

import type { Trace } from '@/services/codeTheater/types';

/** 世界目标种类。MVP 仅 `coverage`（把一组目标逐一作用到），保留联合以便扩展。 */
export type WorldGoalKind = 'coverage';

/**
 * 覆盖型世界目标：要求把 `targets` 里的每个目标都「作用」到一次。
 * 例：浇灌所有旱地 —— targets=[1,2,3,4]、actionName='water'、targetNoun='旱地'。
 *
 * 这是「只有游戏能做」的情境化目标：学生的循环正确与否，体现为世界里
 * 每块地是否都被浇到，而不是一个看不见的 `assert`。
 */
export interface CoverageWorldGoal {
  kind: 'coverage';
  /** 必须被逐一作用到的目标编号（如旱地的第 1~N 块）。约定 1 基、无重复。 */
  readonly targets: readonly number[];
  /**
   * 在世界里「生效」的动作名。学生在 Python 里调用它即对某个目标产生作用，
   * 例如 `water(plot)`。检查器据此从 Trace 推断「哪些目标被作用到」。
   */
  readonly actionName: string;
  /** 目标的人类可读量词名，用于拼可见反馈（如「旱地」）。 */
  readonly targetNoun: string;
}

/** 世界目标联合（当前仅一种；新增种类时在此扩展并保持 checker 穷尽）。 */
export type WorldGoal = CoverageWorldGoal;

/**
 * 一个代码剧场具身任务定义。
 * `kcCodes` 必须绑定真实 packId=43 KC，不得伪造课程内容。
 */
export interface CodeTheaterQuest {
  /** 稳定标识（stage/quest UI 据此加载）。 */
  readonly id: string;
  /** 面向学生的任务标题。 */
  readonly title: string;
  /** 绑定的真实 packId=43 KC code 列表（grounding）。 */
  readonly kcCodes: readonly string[];
  /** NPC 给出的真实需求叙事（不是题面式的「请实现 f(x)」）。 */
  readonly prompt: string;
  /** 可选起手代码：可运行的脚手架，让世界条件反馈成为学习主角。 */
  readonly starterCode?: string;
  /** 可见的世界目标（替代隐藏断言）。 */
  readonly worldGoal: WorldGoal;
}

/**
 * 世界条件检查结果：既是给学生的可见反馈，也是驱动 `world_effect`
 * 渲染指令（契约 §5.3）的数据来源。
 */
export interface WorldCheckResult {
  /** 世界目标是否达成（= 没有遗漏的目标）。 */
  readonly achieved: boolean;
  /** 已被作用到、且属于目标集合的目标（去重、按目标顺序）。 */
  readonly satisfied: readonly number[];
  /** 仍未被作用到的目标（按目标顺序）。如 [4] = 第 4 块旱地还干着（典型 off-by-one）。 */
  readonly unmet: readonly number[];
  /** 被作用到、但不在目标集合内的编号（升序）。如 0 基索引误浇到不存在的「第 0 块」。 */
  readonly outOfRange: readonly number[];
  /** 面向学生的可见反馈：明示世界现状，而非隐藏断言的「错误」。 */
  readonly feedback: string;
}

/**
 * 「被作用目标」提取策略：纯函数，从一条 `Trace` 推断学生对哪些目标生效。
 * 抽成可注入策略，便于 Phase-2 在 trace-engine 的实际事件形态确定后适配
 * （默认按 `call` 事件，见 worldConditionChecker）。
 */
export type ActedTargetExtractor = (trace: Trace, actionName: string) => number[];
