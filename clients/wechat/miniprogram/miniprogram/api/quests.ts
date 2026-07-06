import { request } from '../utils/request';
import {
  Quest,
  QuestCompleteRequest,
  QuestSkipRequest,
  QuestSource,
  QuestStatus,
} from './dto/quests';

export function getTodayQuests(): Promise<Quest[]> {
  return request<Quest[]>('/quests/today');
}

export interface QuestListParams {
  status?: QuestStatus;
  source?: QuestSource;
  limit?: number;
  offset?: number;
}

export function listQuests(params: QuestListParams = {}): Promise<Quest[]> {
  return request<Quest[]>('/quests', {
    query: {
      status: params.status,
      source: params.source,
      limit: params.limit ?? 30,
      offset: params.offset,
    },
  });
}

export function startQuest(id: number): Promise<Quest> {
  return request<Quest>(`/quests/${id}/start`, { method: 'POST' });
}

export function completeQuest(id: number, body: QuestCompleteRequest = {}): Promise<Quest> {
  return request<Quest, QuestCompleteRequest>(`/quests/${id}/complete`, {
    method: 'POST',
    data: body,
  });
}

export function skipQuest(id: number, body: QuestSkipRequest = {}): Promise<Quest> {
  return request<Quest, QuestSkipRequest>(`/quests/${id}/skip`, {
    method: 'POST',
    data: body,
  });
}
