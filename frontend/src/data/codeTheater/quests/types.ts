/**
 * 代码剧场「具身任务」自有类型（C-quest-sample 独占）。
 *
 * 设计要点（见并发契约 §5 与本任务红线）：
 * - 这些类型**只属于 quests/ 目录**，不写进共享 `services/codeTheater/types.ts`。
 * - 任务用「世界条件（WorldCondition / WorldGoal）」替代隐藏断言：达成与否由
 *   学生代码在世界里**留下的可见痕迹**判定，反馈也是可见的世界现状
 *   （如「第 4 盏路灯还黑着」），而非偷偷比对期望输出。
 * - 世界条件检查是纯逻辑：吃一条 `Trace`（契约 §5.2）或抽象世界状态，
 *   产出可独立单测的结构化结果。
 */

import type { Trace } from '@/services/codeTheater/types';

/**
 * 世界目标种类。
 * - `coverage`：把一组目标逐一作用到（顺序不限）。
 * - `sequence`：必须**按给定顺序**逐个作用到目标，错序即未达成。
 */
export type WorldGoalKind = 'coverage' | 'sequence';

/**
 * 覆盖型世界目标：要求把 `targets` 里的每个目标都「作用」到一次。
 * 例：点亮所有路灯 —— targets=[1,2,3,4]、actionName='light'、targetNoun='路灯'、
 * actionVerb='点亮'、unit='盏'。
 *
 * 这是「只有游戏能做」的情境化目标：学生的循环/分支正确与否，体现为世界里
 * 每个目标是否都被作用到，而不是一个看不见的 `assert`。
 */
export interface CoverageWorldGoal {
  kind: 'coverage';
  /** 必须被逐一作用到的目标编号（如第 1~N 盏路灯）。约定 1 基、无重复。 */
  readonly targets: readonly number[];
  /**
   * 在世界里「生效」的动作名 = 学生代码里那个世界动作函数的函数名。学生调用它
   * （如 `light(lamp)`）即对某个目标产生作用；检查器据此从 Trace 的 `call` 事件
   * 推断「哪些目标被作用到」。
   *
   * 重要：该函数须在任务 `starterCode` 中以 `def` 给出——harness 仅追踪「学生文件内
   * 定义」的函数调用（co_filename == "<student>"），未定义的世界动作会直接 NameError、
   * 不会产生 `call` 事件。把动作写进 starterCode 既让真实执行可跑通，也让学生看见
   * 「NPC 给的工具」长什么样。
   */
  readonly actionName: string;
  /** 目标的人类可读名，用于拼可见反馈（如「路灯」）。 */
  readonly targetNoun: string;
  /** 可选：反馈里用的动词（如「点亮」）；缺省回退为 actionName。 */
  readonly actionVerb?: string;
  /** 可选：反馈里目标的量词（如「盏」）；缺省「个」。 */
  readonly unit?: string;
  /**
   * 可选：是否「禁止多作用」。缺省 false——多作用到目标外的编号只算浪费，仍可达成
   * （如多浇一块地）。置 true 时，作用到任何目标外编号即视为未达成（如「只开双数号
   * 城门」的筛选类任务，开了单数号陷阱门就算失败）。让分支/筛选类任务的判定诚实。
   */
  readonly forbidExtra?: boolean;
}

/**
 * 顺序覆盖型世界目标：要求**严格按 `targets` 给定的先后顺序**逐个作用到目标。
 * 与 `coverage` 的唯一区别是「顺序也算数」——少做、错序、把后面的提前做，都判未达成。
 * 例：烽火报警必须从最远的烽火台往回点（targets=[5,4,3,2,1]），先点近的就传不回信号。
 *
 * 这同样是「只有游戏能做」的情境化目标：学生循环方向/起止写错（如顺序点成 1→5），
 * 体现为世界里「信号没按链路接上」，而非一个看不见的顺序断言。
 */
export interface SequenceWorldGoal {
  kind: 'sequence';
  /**
   * 必须**按此数组先后顺序**逐个作用到的目标编号。约定正整数、无重复；
   * 数组顺序即规定顺序（不要求升序，逆序/乱序皆可，刻意区别于 coverage）。
   */
  readonly targets: readonly number[];
  /**
   * 世界动作函数名（学生在 `starterCode` 里 `def` 出来的那个）。检查器据此从 Trace 的
   * `call` 事件按发生顺序读出「学生实际作用的顺序」，再逐位与 `targets` 比对。
   * 约束与 {@link CoverageWorldGoal.actionName} 相同：必须 `def` 在 starterCode 内，
   * 且把目标编号作为第一个实参。
   */
  readonly actionName: string;
  /** 目标的人类可读名，用于拼可见反馈（如「烽火台」）。 */
  readonly targetNoun: string;
  /** 可选：反馈里用的动词（如「点燃」）；缺省回退为 actionName。 */
  readonly actionVerb?: string;
  /** 可选：反馈里目标的量词（如「座」）；缺省「个」。 */
  readonly unit?: string;
}

/** 世界目标联合（新增种类时在此扩展，并保持 checker 的 switch 穷尽）。 */
export type WorldGoal = CoverageWorldGoal | SequenceWorldGoal;

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
  /** 世界目标是否达成（coverage = 无遗漏目标；sequence = 顺序也完全正确）。 */
  readonly achieved: boolean;
  /**
   * 已达成的目标（去重、按目标顺序）。
   * coverage：已被作用到且属于目标集合的目标；sequence：按规定顺序正确完成的「前缀」目标。
   */
  readonly satisfied: readonly number[];
  /**
   * 仍未达成的目标（按目标顺序）。如 [4] = 第 4 盏路灯还黑着（典型 off-by-one）。
   * sequence：从「第一处顺序出错」起、其后的目标都算未达成。
   */
  readonly unmet: readonly number[];
  /** 被作用到、但不在目标集合内的编号（升序）。如 0 基索引误作用到不该作用的「第 0 个」。 */
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
