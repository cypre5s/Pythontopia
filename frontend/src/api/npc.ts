import { http } from './http';
import type { MemoryItemDto } from './dto/Memory';

export type { MemoryItemDto };

export interface NpcDialogRequest {
  message: string;
}

export interface NpcDialogResponse {
  replyMd: string;
  citedEventIds: number[];
  grounded: boolean;
}

export async function fetchNpcMemories(
  npcKey: string,
  focusKc?: string,
  limit?: number,
): Promise<MemoryItemDto[]> {
  const params: Record<string, string | number> = {};
  if (focusKc) {
    params.focusKc = focusKc;
  }
  if (limit !== undefined) {
    params.limit = limit;
  }
  const response = await http.get<MemoryItemDto[]>(
    `/api/npc/${encodeURIComponent(npcKey)}/memory`,
    { params },
  );
  return response.data;
}

export async function postNpcDialog(
  npcKey: string,
  request: NpcDialogRequest,
): Promise<NpcDialogResponse> {
  const response = await http.post<NpcDialogResponse>(
    `/api/npc/${encodeURIComponent(npcKey)}/dialog`,
    request,
  );
  return response.data;
}

export async function talkToMemoryNpc(
  npcKey: string,
  message: string,
): Promise<NpcDialogResponse> {
  return postNpcDialog(npcKey, { message });
}
