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
 * battle 偏代码判题，event 偏概念，bug 偏改错，elite 偏综合，boss 偏组合大题。
 */
export const NODE_KIND_POOLS: Record<RogueNodeKind, ArenaLevelKind[]> = {
  battle: ['dual_track', 'fill_blank', 'closed_loop'],
  elite: ['dual_track', 'multi_round'],
  event: ['short_answer', 'pdf_question'],
  bug: ['debug', 'bug_mine'],
  chest: [],
  rest: [],
  boss: ['multi_round', 'dual_track'],
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
