import { http } from './http';
import type {
  NpcDialogStateDto,
  NpcGiftRequest,
  NpcGiftResponseDto,
  NpcTalkRequest,
  NpcTalkResponseDto,
} from './dto/NpcDialogStateDto';

export async function fetchNpcState(npcKey: string): Promise<NpcDialogStateDto> {
  const response = await http.get<NpcDialogStateDto>(
    `/api/world/npcs/${encodeURIComponent(npcKey)}/state`,
  );
  return response.data;
}

export async function talkToNpc(
  npcKey: string,
  payload: NpcTalkRequest,
): Promise<NpcTalkResponseDto> {
  const response = await http.post<NpcTalkResponseDto>(
    `/api/world/npcs/${encodeURIComponent(npcKey)}/talk`,
    payload,
  );
  return response.data;
}

export async function giftNpc(
  npcKey: string,
  payload: NpcGiftRequest,
): Promise<NpcGiftResponseDto> {
  const response = await http.post<NpcGiftResponseDto>(
    `/api/world/npcs/${encodeURIComponent(npcKey)}/gift`,
    payload,
  );
  return response.data;
}
