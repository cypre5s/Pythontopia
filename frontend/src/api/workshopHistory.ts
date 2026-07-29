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
const activeHistoryRequests = new Set<AbortController>();
let historyCacheGeneration = 0;

function historyCacheKey(userId: number, limit: number): string {
  return `${userId}:${limit}`;
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
  userId: number,
  limit = 50,
  options: ListWorkshopChatHistoryOptions = {},
): Promise<WorkshopChatHistoryTurnDto[]> {
  const now = resolveNow(options);
  const ttlMs = options.ttlMs ?? DEFAULT_HISTORY_CACHE_TTL_MS;
  const cacheKey = historyCacheKey(userId, limit);
  const cached = historyCache.get(cacheKey);
  if (!options.force && cached && now - cached.loadedAt < ttlMs) {
    return cloneTurns(cached.turns);
  }

  const requestGeneration = historyCacheGeneration;
  const controller = new AbortController();
  activeHistoryRequests.add(controller);
  try {
    const response = await http.get<WorkshopChatHistoryTurnDto[]>(
      '/api/workshop/chat-history/turns',
      { params: { limit }, signal: controller.signal },
    );
    if (requestGeneration === historyCacheGeneration) {
      historyCache.set(cacheKey, { loadedAt: now, turns: cloneTurns(response.data) });
    }
    return cloneTurns(response.data);
  } finally {
    activeHistoryRequests.delete(controller);
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
  userId: number,
  turn: WorkshopChatHistoryTurnDto,
  limit = 50,
  options: RememberWorkshopChatHistoryOptions = {},
): void {
  const cacheKey = historyCacheKey(userId, limit);
  const cached = historyCache.get(cacheKey);
  if (!cached) return;

  const nextTurns = cloneTurns(cached.turns);
  const existingIndex = nextTurns.findIndex((item) => item.id === turn.id);
  if (existingIndex >= 0) {
    nextTurns[existingIndex] = cloneTurns([turn])[0]!;
  } else {
    nextTurns.unshift(cloneTurns([turn])[0]!);
  }
  historyCache.set(cacheKey, {
    loadedAt: resolveNow(options),
    turns: nextTurns.slice(0, limit),
  });
}

export function clearWorkshopChatHistoryCache(): void {
  historyCacheGeneration += 1;
  for (const controller of activeHistoryRequests) {
    controller.abort();
  }
  activeHistoryRequests.clear();
  historyCache.clear();
}

export function resetWorkshopChatHistoryCacheForTest(): void {
  clearWorkshopChatHistoryCache();
}
