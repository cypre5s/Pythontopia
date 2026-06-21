// 无尽远征「知识前沿选择器」+ run 跨会话持久化前端 client。
//
// - select-frontier：调后端 POST /api/world/boss/select-frontier（后端读权威掌握度 + graph 排序 +
//   间隔重复）。失败/超时由调用方回退本地 useKcFrontier——绝不阻塞玩家，与 boss 出题降级同哲学。
// - expedition/run：GET/PUT/DELETE /api/world/expedition/run，把 run 进度升级为后端权威（跨设备/
//   会话续局）。所有请求 suppressNetworkToast + 短超时，失败由调用方回退 localStorage，绝不阻塞。

import { http } from './http';
import type { ProfileSignalsDto } from '@/api/dto/Boss';
import type { RogueHistoryEntry, RogueNode, RogueRunPhase } from '@/types/rogue';

export interface FrontierPick {
  kcCode: string;
  chapter: number;
  difficulty: number;
  reason: string;
}

interface FrontierSelectResult {
  picks: FrontierPick[];
}

export interface FrontierSelectInput {
  focusChapter?: number | null;
  /** 近期练过的 KC（最近在前），间隔重复用。 */
  recentKcs?: string[];
  /** 本局已用 KC，避免重复。 */
  exclude?: string[];
  /** 需要的候选数量。 */
  count: number;
  profileSignals?: ProfileSignalsDto;
}

/** 取一批知识前沿候选；失败抛错（调用方 catch 后回退本地选择器）。 */
export async function requestFrontierPicks(input: FrontierSelectInput): Promise<FrontierPick[]> {
  const body: Record<string, unknown> = {
    recentKcs: input.recentKcs ?? [],
    exclude: input.exclude ?? [],
    count: input.count,
  };
  if (input.focusChapter != null) body.focusChapter = input.focusChapter;
  if (input.profileSignals) body.profileSignals = input.profileSignals;
  const res = await http.post<FrontierSelectResult>('/api/world/boss/select-frontier', body, {
    suppressNetworkToast: true,
    timeout: 3000,
  });
  return Array.isArray(res.data?.picks) ? res.data.picks : [];
}

// ── run 跨会话持久化（后端权威；前端 stores/rogueRun 后端优先 + localStorage 兜底） ──

/** 一局远征 run 的可持久化快照（与 stores/rogueRun PersistShape 同构，后端 expedition_run 表存它）。 */
export interface ExpeditionRunSnapshot {
  phase: RogueRunPhase;
  focusChapter: number | null;
  nodes: RogueNode[];
  layers: number;
  currentNodeId: string | null;
  hp: number;
  maxHp: number;
  momentum: number;
  bestMomentum: number;
  score: number;
  relics: string[];
  isDaily: boolean;
  history: RogueHistoryEntry[];
}

const RUN_PATH = '/api/world/expedition/run';

/** 取当前用户的活跃 run；无活跃 run（204/空）返回 null。失败抛错（调用方回退本地）。 */
export async function fetchExpeditionRun(): Promise<ExpeditionRunSnapshot | null> {
  const res = await http.get<ExpeditionRunSnapshot>(RUN_PATH, {
    suppressNetworkToast: true,
    timeout: 3000,
    // 204（无活跃 run）不算错误，照常 resolve 由下方映射成 null。
    validateStatus: (status: number) => status === 200 || status === 204,
  });
  if (res.status === 204 || !res.data) return null;
  return res.data;
}

/** 保存（upsert）当前用户的活跃 run。失败抛错（调用方 best-effort 吞掉，本地仍有兜底）。 */
export async function saveExpeditionRun(
  run: ExpeditionRunSnapshot,
): Promise<ExpeditionRunSnapshot | undefined> {
  const res = await http.put<ExpeditionRunSnapshot>(RUN_PATH, run, {
    suppressNetworkToast: true,
    timeout: 3000,
  });
  return res.data;
}

/** 结束/弃局：删除当前用户的活跃 run。失败抛错（调用方 best-effort 吞掉）。 */
export async function deleteExpeditionRun(): Promise<void> {
  await http.delete(RUN_PATH, {
    suppressNetworkToast: true,
    timeout: 3000,
  });
}

// ── 班级排行榜（最高单局得分；后端真实数据，未入班为空榜） ──

export interface ExpeditionLeaderboardEntry {
  rank: number;
  userId: number;
  displayName: string;
  bestScore: number;
  bestDepth: number;
  self: boolean;
}

export interface ExpeditionLeaderboard {
  entries: ExpeditionLeaderboardEntry[];
  /** 自身全班名次（1 起；未上榜 / 未玩过为 null）。 */
  selfRank: number | null;
  selfBest: number;
  inClass: boolean;
}

export interface ExpeditionScorePayload {
  score: number;
  depth: number;
  bestMomentum: number;
  cleared: boolean;
  daily: boolean;
}

const SCORE_PATH = '/api/world/expedition/score';
const LEADERBOARD_PATH = '/api/world/expedition/leaderboard';

/** 上报一局成绩，返回最新班级榜；失败返回 null（best-effort，不阻塞结算）。 */
export async function submitExpeditionScore(
  payload: ExpeditionScorePayload,
): Promise<ExpeditionLeaderboard | null> {
  try {
    const res = await http.post<ExpeditionLeaderboard>(SCORE_PATH, payload, {
      suppressNetworkToast: true,
      timeout: 3000,
    });
    return res.data ?? null;
  } catch {
    return null;
  }
}

/** 取当前学生班级排行榜；失败返回 null（best-effort）。 */
export async function fetchExpeditionLeaderboard(): Promise<ExpeditionLeaderboard | null> {
  try {
    const res = await http.get<ExpeditionLeaderboard>(LEADERBOARD_PATH, {
      suppressNetworkToast: true,
      timeout: 3000,
    });
    return res.data ?? null;
  } catch {
    return null;
  }
}
