// 无尽远征「知识前沿选择器」+ run 跨会话持久化前端 client。
//
// - select-frontier：调后端 POST /api/world/boss/select-frontier（后端读权威掌握度、服务端画像 +
//   graph 排序 + 间隔重复）。
// - expedition/run：POST 开局由服务端生成地图；POST /actions 写入服务端权威进度。
//   GET/DELETE 读取或弃局；PUT 仅保留旧客户端的快照校验兼容。

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
  /** 本局已用 KC，避免重复。 */
  exclude?: string[];
  /** 需要的候选数量。 */
  count: number;
  profileSignals?: ProfileSignalsDto;
}

export interface ExpeditionRunStartInput {
  focusChapter: number | null;
  isDaily: boolean;
}

export type ExpeditionRunAction =
  | { runId: string; action: 'ENTER_NODE'; nodeId: string }
  | { runId: string; action: 'EVENT_REVIEW' | 'EVENT_STUDY' | 'EVENT_STEADY'; nodeId: string }
  | { runId: string; action: 'CLAIM_RELIC'; nodeId: string; relicId: string }
  | { runId: string; action: 'SETTLE_CHALLENGE'; nodeId: string; problemId: string }
  | { runId: string; action: 'FINISH' };

/** 取一批知识前沿候选；空结果/坏结构与网络错误一样显式失败。 */
export async function requestFrontierPicks(input: FrontierSelectInput): Promise<FrontierPick[]> {
  const body: Record<string, unknown> = {
    exclude: input.exclude ?? [],
    count: input.count,
  };
  if (input.focusChapter != null) body.focusChapter = input.focusChapter;
  if (input.profileSignals) body.profileSignals = input.profileSignals;
  const res = await http.post<FrontierSelectResult>('/api/world/boss/select-frontier', body, {
    suppressNetworkToast: true,
    timeout: 3000,
  });
  if (!Array.isArray(res.data?.picks) || res.data.picks.length === 0) {
    throw new Error('服务端未返回画像学习前沿');
  }
  const valid = res.data.picks.every((pick) =>
    typeof pick?.kcCode === 'string'
    && /^PY43-KC-\d{3}$/.test(pick.kcCode)
    && Number.isInteger(pick.chapter)
    && pick.chapter >= 1
    && pick.chapter <= 7
    && Number.isInteger(pick.difficulty)
    && pick.difficulty >= 1
    && pick.difficulty <= 5,
  );
  if (!valid) throw new Error('服务端画像学习前沿结构不完整');
  return res.data.picks;
}

// ── run 跨会话持久化（后端权威；前端 stores/rogueRun 后端优先 + localStorage 兜底） ──

/** 一局远征 run 的可持久化快照（与 stores/rogueRun PersistShape 同构，后端 expedition_run 表存它）。 */
export interface ExpeditionRunSnapshot {
  /** 后端为当前玩家存档签发的 run 标识。 */
  runId?: string | null;
  phase: RogueRunPhase;
  focusChapter: number | null;
  nodes: RogueNode[];
  layers: number;
  currentNodeId: string | null;
  hp: number;
  maxHp: number;
  momentum: number;
  bestMomentum: number;
  /** Server response value from verified judged attempts; ignored when sent on save requests. */
  score: number;
  /** Server-derived count of passed judged attempts; absent from local-only saves. */
  verifiedDepth?: number;
  relics: string[];
  isDaily: boolean;
  history: RogueHistoryEntry[];
}

const RUN_PATH = '/api/world/expedition/run';

/** Starts a run whose frontier, map and initial gameplay state are issued by the server. */
export async function createExpeditionRun(
  input: ExpeditionRunStartInput,
): Promise<ExpeditionRunSnapshot> {
  const res = await http.post<ExpeditionRunSnapshot>(RUN_PATH, input, {
    suppressNetworkToast: true,
    timeout: 3000,
  });
  return res.data;
}

/** Applies one typed gameplay command; the returned snapshot is server-authoritative. */
export async function performExpeditionAction(
  action: ExpeditionRunAction,
): Promise<ExpeditionRunSnapshot> {
  const res = await http.post<ExpeditionRunSnapshot>(`${RUN_PATH}/actions`, action, {
    suppressNetworkToast: true,
    timeout: 3000,
  });
  return res.data;
}

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

/** 校验旧客户端快照并返回服务端进度；此兼容接口不会写入游戏进度。 */
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
  runId: string;
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
