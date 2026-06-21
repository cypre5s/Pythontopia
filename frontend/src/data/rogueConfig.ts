// 无尽远征配置 + KC↔章节工具。
//
// KC 编码固定 PY43-KC-001..065，章节区间与 useEndlessRun.pickWeakKcForChapter / bossCatalog 一致。

import type { ArenaLevelKind } from '@/types/arena';
import type { RogueNodeKind } from '@/types/rogue';

/** [章节, KC 起编号, KC 止编号]（含端点）。 */
export const KC_CHAPTER_RANGES: readonly (readonly [number, number, number])[] = [
  [1, 1, 7], [2, 8, 16], [3, 17, 30], [4, 31, 38], [5, 39, 47], [6, 48, 53], [7, 54, 65],
];

export const KC_TOTAL = 65;

function kcCode(num: number): string {
  return `PY43-KC-${String(num).padStart(3, '0')}`;
}

/** 全部 65 个 KC 编码。 */
export function allKcCodes(): string[] {
  return Array.from({ length: KC_TOTAL }, (_, i) => kcCode(i + 1));
}

/** 某 KC 属于第几章（解析不出时回退第 1 章）。 */
export function kcChapter(code: string): number {
  const m = /PY43-KC-(\d+)/.exec(code);
  if (!m) return 1;
  const num = Number(m[1]);
  const range = KC_CHAPTER_RANGES.find(([, lo, hi]) => num >= lo && num <= hi);
  return range ? range[0] : 1;
}

/** 某章的全部 KC 编码。 */
export function chapterKcs(chapter: number): string[] {
  const range = KC_CHAPTER_RANGES.find(([c]) => c === chapter);
  if (!range) return [];
  const [, lo, hi] = range;
  return Array.from({ length: hi - lo + 1 }, (_, i) => kcCode(lo + i));
}

/**
 * 节点类型 → 题型候选池（加权随机取一个）。chest/rest 无题型。
 *
 * 健壮性约束（2026-06-21）：后端 boss 判题只做**代码沙箱判题**（把提交内容当 Python 跑测试用例），
 * 且 graph 出题流水线只产出「写程序」类代码题。因此题型池只收录**学生最终提交可运行 Python 代码、
 * 能被真判通过**的题型：
 *   - dual_track：写完整程序（最贴合现场出的代码题）；
 *   - closed_loop：读→定位→修复，最终在「修复」步写出完整代码；
 *   - debug / bug_mine：在含 bug 的初始代码上改出正确解（bug 节点的「抓 bug」主题）。
 * 已移除会导致**必败/卡死**的题型（与真判题不兼容）：
 *   - short_answer / pdf_question：提交散文 → 当代码跑 → SyntaxError；
 *   - fill_blank：现场代码题题面无 `___` 占位 → 实际提交的是题面散文；
 *   - multi_round：多段散文+代码拼接 → 非法 Python。
 * 这样保证「每个节点都能真判通过、boss 必可通关」。概念/叙事性仍由 event 的情景文案 + 三选一承载。
 */
export const NODE_KIND_POOLS: Record<RogueNodeKind, ArenaLevelKind[]> = {
  battle: ['dual_track', 'closed_loop'],
  elite: ['dual_track', 'closed_loop'],
  event: ['dual_track'],
  bug: ['debug', 'bug_mine'],
  chest: [],
  rest: [],
  boss: ['dual_track'],
};

/** 节点类型中文名（地图节点标签 / 结算用）。 */
export const NODE_KIND_LABEL: Record<RogueNodeKind, string> = {
  battle: '判题战斗',
  elite: '精英挑战',
  event: '概念考点',
  bug: '抓 Bug',
  chest: '资源宝箱',
  rest: '复盘营地',
  boss: '知识闸 · BOSS',
};

export const NODE_KIND_ICON: Record<RogueNodeKind, string> = {
  battle: '⚔',
  elite: '✦',
  event: '❓',
  bug: '🐞',
  chest: '🎁',
  rest: '⛺',
  boss: '★',
};

/** 地图形状默认值。 */
export const EXPEDITION_LAYERS = 9; // 含起点层；boss 单独追加一层 → 实际 layers = EXPEDITION_LAYERS + 1
export const LAYER_MIN_WIDTH = 2;
export const LAYER_MAX_WIDTH = 4;

/** HP / 计分。 */
export const EXPEDITION_MAX_HP = 5;
export const REST_HEAL = 1;

/**
 * 局内自适应难度（「手感层」，不改地图生成时定下的基准难度真源 node.difficulty）。
 *
 * - 连胜攒气势 → 抬难度：气势 ≥3 → +1，≥6 → +2（让连胜玩家「越打越上头」，对齐重构计划 §4
 *   「连胜拉高下一题难度与得分系数」）；
 * - 近期连败 → 温和下调：最近两题皆错 → -1（脆弱学生临时降压，符合「温和 HP」哲学）；
 * - 结果 clamp 到 1..5。
 *
 * 纯函数（无副作用），便于单测；得分侧的气势加成仍由 store.scoreFor 的 momentumBonus 负责。
 */
export function computeRunDifficulty(
  base: number,
  momentum: number,
  recentFailures: number,
): number {
  let d = base;
  if (momentum >= 6) d += 2;
  else if (momentum >= 3) d += 1;
  if (recentFailures >= 2) d -= 1;
  return Math.max(1, Math.min(5, Math.round(d)));
}
