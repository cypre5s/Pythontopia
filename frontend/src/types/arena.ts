// R-13 §-1 续：/arena/:chapter 章节闯关塔类型。
//
// 用户 Q2=A 决策：每章 10 关阶梯，前 9 关知识/技能小关，第 10 关是 boss。
// 70 关 = 7 章 × 10 关；前 9 关复用 EndlessPage 题目机制（按 focusChapter + kc），
// 第 10 关跳转 BossPage 4 阶段副本。
//
// 关卡状态走 localStorage 持久化（与 mainQuest 一致），离线可玩。

/** 关卡题型（用于在阶梯卡片上显示图标 + 在 EndlessPage 提示偏好类型）。 */
export type ArenaLevelKind =
  | 'pdf_question'   // 看 PDF + 单选 / 简答
  | 'fill_blank'     // 代码填空
  | 'debug'          // 改错题
  | 'short_answer'   // 简答 + grounding 评分
  | 'closed_loop'    // 调试闭环（堆栈定位 → 修）
  | 'dual_track'     // 双轨：Pyodide 预览 + 后端判题
  | 'multi_round'    // 多轮组合（3 题 input/print 等）
  | 'teach_back'     // 教学反转（向 NPC 解释概念）
  | 'bug_mine'       // 随机 ImportError 30 秒挑战
  | 'boss';          // 章末 boss 4 阶段副本

export interface ArenaLevel {
  /** 关卡 ID，形如 "1-1", "1-2", ..., "1-10"（10 = boss）。 */
  id: string;
  /** 1..7，章节。 */
  chapter: number;
  /** 1..10，本章关号。 */
  index: number;
  /** 关卡题型，决定走哪条评测链。 */
  kind: ArenaLevelKind;
  /** 关卡标题。 */
  title: string;
  /** 玩法 + 通关条件提示。 */
  hint: string;
  /** 主要锚定的 KC code 列表（来自 packId=43 真课程包）。 */
  kcCodes: readonly string[];
  /** 1..5 难度，与 boss problem 接的 difficulty 对齐。 */
  difficulty: 1 | 2 | 3 | 4 | 5;
  /** 通过后奖励。 */
  rewards: readonly { kind: 'coin' | 'kc_mastery'; amount: number; ref?: string }[];
}

/** ArenaLevel 在 store 中持有的进度。 */
export interface ArenaLevelProgress {
  /** 是否通关 */
  cleared: boolean;
  /** 累计尝试次数 */
  attempts: number;
  /** 最近一次通过用时（秒），未通过时为 null */
  lastClearedSec: number | null;
  /** 最近一次通过时间戳，ISO 字符串 */
  lastClearedAt: string | null;
}
