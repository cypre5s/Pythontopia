// 玩家存档槽 API 客户端（对齐后端 SaveSlotController `/api/world/saves`）。
//
// R-15：旧「把 mainQuest 主线进度写入 save_slot.snapshot.mainQuest」用途已退役
// （主线进度迁至后端权威 mainline 域，按 user_id 独立表）。本模块保留通用的存档槽
// 创建 / 激活 / 列表 / 快照写入 API 客户端，供其它存档相关功能按需调用。

import { http } from './http';
import type { SaveSlotDto, SaveSlotSnapshot } from './dto/SaveSlotDto';

export async function listSaveSlots(): Promise<SaveSlotDto[]> {
  const { data } = await http.get<SaveSlotDto[]>('/api/world/saves');
  return data;
}

export async function createSaveSlot(slotIndex: number, displayName: string): Promise<SaveSlotDto> {
  const { data } = await http.post<SaveSlotDto>(`/api/world/saves/${slotIndex}`, { displayName });
  return data;
}

export async function activateSaveSlot(slotIndex: number): Promise<SaveSlotDto> {
  const { data } = await http.post<SaveSlotDto>(`/api/world/saves/${slotIndex}/activate`);
  return data;
}

/**
 * 写入存档槽快照。后端是 `@RequestBody String snapshot` + `SET snapshot = ?::jsonb`，
 * 所以这里必须发送**序列化后的 JSON 文本**而非对象（Spring 的 StringHttpMessageConverter
 * 支持 `*\/*`，会先于 Jackson 把请求体原样读为 String）。
 */
export async function saveSlotSnapshot(
  slotIndex: number,
  snapshot: SaveSlotSnapshot,
): Promise<SaveSlotDto> {
  const { data } = await http.post<SaveSlotDto>(
    `/api/world/saves/${slotIndex}/snapshot`,
    JSON.stringify(snapshot),
    { headers: { 'Content-Type': 'application/json' } },
  );
  return data;
}
