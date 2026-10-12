import { http } from './http';

export type WorkshopHistoryResponseKind =
  | 'answer'
  | 'resource'
  | 'video'
  | 'browse'
  | 'error'
  | 'clarify'
  | 'plan'
  | 'progress'
  | 'mystudy'
  | 'navigate'
  | 'secondary';

export interface WorkshopChatHistoryTurnDto {
  id: string;
  conversationId: string;
  userText: string;
  intent: string;
  responseKind: WorkshopHistoryResponseKind;
  toolLabel: string | null;
  responseText: string | null;
  responsePayload: Record<string, unknown>;
  evidencePageIds: number[];
  groundingStatus: string | null;
  createdAt: string;
}

export interface WorkshopChatHistoryRecordRequest {
  conversationId: string;
  userText: string;
  intent: string;
  responseKind: WorkshopHistoryResponseKind;
  toolLabel?: string | null;
  responseText?: string | null;
  responsePayload?: Record<string, unknown>;
}

export interface WorkshopChatHistoryRecordResponse {
  id: number;
}

interface WorkshopHistoryCacheEntry {
  loadedAt: number;
  turns: WorkshopChatHistoryTurnDto[];
}

export interface ListWorkshopChatHistoryOptions {
  force?: boolean;
  now?: () => number;
  ttlMs?: number;
}

export interface RememberWorkshopChatHistoryOptions {
  now?: () => number;
}

const DEFAULT_HISTORY_CACHE_TTL_MS = 60_000;
const historyCache = new Map<string, WorkshopHistoryCacheEntry>();
const requestSequences = new Map<string, number>();
const activeRequests = new Map<string, Set<AbortController>>();
let cacheGeneration = 0;

function cacheKey(ownerId: number, limit: number): string {
  return `${ownerId}:${limit}`;
}

function nextRequestSequence(key: string): number {
  const next = (requestSequences.get(key) ?? 0) + 1;
  requestSequences.set(key, next);
  return next;
}

function abortRequestsForKey(key: string): void {
  const controllers = activeRequests.get(key);
  if (!controllers) return;
  for (const controller of controllers) controller.abort();
  activeRequests.delete(key);
}

function trackRequest(key: string, controller: AbortController): void {
  const controllers = activeRequests.get(key) ?? new Set<AbortController>();
  controllers.add(controller);
  activeRequests.set(key, controllers);
}

function untrackRequest(key: string, controller: AbortController): void {
  const controllers = activeRequests.get(key);
  if (!controllers) return;
  controllers.delete(controller);
  if (controllers.size === 0) activeRequests.delete(key);
}

function cloneTurns(turns: WorkshopChatHistoryTurnDto[]): WorkshopChatHistoryTurnDto[] {
  return turns.map((turn) => ({
    ...turn,
    responsePayload: { ...turn.responsePayload },
    evidencePageIds: [...turn.evidencePageIds],
  }));
}

function resolveNow(options?: { now?: () => number }): number {
  return options?.now?.() ?? Date.now();
}

export async function listWorkshopChatHistoryTurns(
  ownerId: number,
  limit = 50,
  options: ListWorkshopChatHistoryOptions = {},
): Promise<WorkshopChatHistoryTurnDto[]> {
  const key = cacheKey(ownerId, limit);
  const now = resolveNow(options);
  const ttlMs = options.ttlMs ?? DEFAULT_HISTORY_CACHE_TTL_MS;
  const cached = historyCache.get(key);
  if (!options.force && cached && now - cached.loadedAt < ttlMs) {
    return cloneTurns(cached.turns);
  }

  // 同一 owner+limit 的强制刷新取代旧读；即使测试替身忽略 abort，sequence 也会阻止晚到覆盖。
  abortRequestsForKey(key);
  const controller = new AbortController();
  const generation = cacheGeneration;
  const sequence = nextRequestSequence(key);
  trackRequest(key, controller);
  try {
    const response = await http.get<WorkshopChatHistoryTurnDto[]>(
      '/api/workshop/chat-history/turns',
      {
        params: { limit },
        signal: controller.signal,
        // 账号切换主动取消属于正常生命周期，不能冒充网络故障弹全局 toast。
        suppressNetworkToast: true,
      },
    );
    if (
      generation === cacheGeneration
      && sequence === requestSequences.get(key)
      && !controller.signal.aborted
    ) {
      historyCache.set(key, { loadedAt: now, turns: cloneTurns(response.data) });
    }
    return cloneTurns(response.data);
  } finally {
    untrackRequest(key, controller);
  }
}

export async function recordWorkshopChatHistoryTurn(
  request: WorkshopChatHistoryRecordRequest,
): Promise<WorkshopChatHistoryRecordResponse> {
  const response = await http.post<WorkshopChatHistoryRecordResponse>(
    '/api/workshop/chat-history/turns',
    request,
  );
  return response.data;
}

export function rememberWorkshopChatHistoryTurn(
  ownerId: number,
  turn: WorkshopChatHistoryTurnDto,
  limit = 50,
  options: RememberWorkshopChatHistoryOptions = {},
): void {
  const key = cacheKey(ownerId, limit);
  // 保存结果比此前发出的读请求更新。即使尚未建立缓存，也必须作废旧读，
  // 否则旧响应晚到后会把刚保存的 turn 从会话缓存中“抹掉”。
  abortRequestsForKey(key);
  nextRequestSequence(key);
  const cached = historyCache.get(key);
  if (!cached) return;

  const nextTurns = cloneTurns(cached.turns);
  const existingIndex = nextTurns.findIndex((item) => item.id === turn.id);
  if (existingIndex >= 0) {
    nextTurns[existingIndex] = cloneTurns([turn])[0]!;
  } else {
    nextTurns.unshift(cloneTurns([turn])[0]!);
  }
  historyCache.set(key, {
    loadedAt: resolveNow(options),
    turns: nextTurns.slice(0, limit),
  });
}

/** 账号生命周期边界：取消在途读、作废晚到结果并丢弃全部用户缓存。 */
export function clearWorkshopChatHistoryCache(): void {
  cacheGeneration += 1;
  for (const controllers of activeRequests.values()) {
    for (const controller of controllers) controller.abort();
  }
  activeRequests.clear();
  requestSequences.clear();
  historyCache.clear();
}

export function resetWorkshopChatHistoryCacheForTest(): void {
  clearWorkshopChatHistoryCache();
}
