// 无尽远征（Roguelike）类型。
//
// 一局 run = 一张分层 DAG（类杀戮尖塔）：玩家从底层沿出边选择下一节点向上闯，
// 终点为 boss。节点的 KC / 题型 / 难度由「知识前沿选择器」在生成地图时按掌握度 + 画像确定。
// 详见 docs/plans/2026-06-19-endless-roguelike-rebuild.md。

import type { ArenaLevelKind } from './arena';

export type RogueNodeKind =
  | 'battle' // 常规判题
  | 'elite'  // 高难判题（难度 +1，奖励更高）
  | 'event'  // 概念考点（简答 / 课件问答）
  | 'bug'    // 抓 bug（改错 / Bug 矿洞）
  | 'chest'  // 宝箱：去百晓工坊领本 KC 资源
  | 'rest'   // 营地：回血 + 知识点复盘
  | 'boss';  // 知识闸：像素 boss，过关回流 mainline 补星

export type RogueNodeState = 'locked' | 'available' | 'current' | 'cleared' | 'bypassed';

export interface RogueNode {
  /** 节点 id，形如 "L2-1"。 */
  id: string;
  /** 所在层（0 = 起点层）。 */
  layer: number;
  /** 层内列序（用于布局与连边）。 */
  col: number;
  kind: RogueNodeKind;
  /** 锚定 KC（battle/elite/event/bug/boss 有；chest/rest 为 null）。 */
  kcCode: string | null;
  /** 题型（映射到 ArenaLevelKind，供 KindRouter / 出题用）；chest/rest 为 null。 */
  challengeKind: ArenaLevelKind | null;
  /** 难度 1..5。 */
  difficulty: number;
  /** 出边：可前往的下一层节点 id 列表。 */
  next: string[];
  state: RogueNodeState;
}

export interface RogueMap {
  /** 扁平节点列表（含 layer/col，渲染时按 layer 分组）。 */
  nodes: RogueNode[];
  /** 总层数（含起点层与 boss 层）。 */
  layers: number;
}

export type RogueRunPhase =
  | 'idle'        // 未开始
  | 'map'         // 在地图上选节点
  | 'event'       // 概念事件节点：选应对（钻研/稳练/复盘）
  | 'challenge'   // 进入某节点答题中
  | 'reward'      // 宝箱 / 精英通关后「三选一」领学习增益
  | 'cleared'     // 打通 boss，本局胜利
  | 'over';       // HP 归零，本局结束

export interface RogueHistoryEntry {
  nodeId: string;
  kcCode: string | null;
  kind: RogueNodeKind;
  passed: boolean;
  score: number;
  at: string;
}
