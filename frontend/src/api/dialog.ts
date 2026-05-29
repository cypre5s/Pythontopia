import { http } from './http';
import type { DialogContextDto } from './dto/DialogContextDto';

/**
 * 拉取 NPC 对话渲染上下文。
 *
 * @param npcKey 可选 NPC key，仅用于 `giftJustGiven` 判定；缺省时关闭礼物分支。
 */
export async function fetchDialogContext(npcKey?: string): Promise<DialogContextDto> {
  const url = npcKey ? `/api/dialog/context?npcKey=${encodeURIComponent(npcKey)}` : '/api/dialog/context';
  const response = await http.get<DialogContextDto>(url);
  return response.data;
}
