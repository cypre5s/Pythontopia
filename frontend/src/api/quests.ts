import { http } from './http';
import type {
  QuestCompleteRequest,
  QuestDto,
  QuestSkipRequest,
  QuestSource,
  QuestStatus,
} from './dto/Quest';

export async function getTodayQuests(): Promise<QuestDto[]> {
  const response = await http.get<QuestDto[]>('/api/quests/today');
  return response.data;
}

export interface QuestListQuery {
  status?: QuestStatus;
  source?: QuestSource;
  limit?: number;
  offset?: number;
}

export async function listQuests(query: QuestListQuery = {}): Promise<QuestDto[]> {
  const params: Record<string, string | number> = {};
  if (query.status) params.status = query.status;
  if (query.source) params.source = query.source;
  if (query.limit !== undefined) params.limit = query.limit;
  if (query.offset !== undefined) params.offset = query.offset;
  const response = await http.get<QuestDto[]>('/api/quests', { params });
  return response.data;
}

export async function startQuest(id: number): Promise<QuestDto> {
  const response = await http.post<QuestDto>(`/api/quests/${id}/start`, {});
  return response.data;
}

export async function completeQuest(
  id: number,
  request: QuestCompleteRequest = {},
): Promise<QuestDto> {
  const response = await http.post<QuestDto>(`/api/quests/${id}/complete`, request);
  return response.data;
}

export async function skipQuest(
  id: number,
  request: QuestSkipRequest = {},
): Promise<QuestDto> {
  const response = await http.post<QuestDto>(`/api/quests/${id}/skip`, request);
  return response.data;
}
