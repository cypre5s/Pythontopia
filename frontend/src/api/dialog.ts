import { http } from './http';
import type { DialogContextDto } from './dto/DialogContextDto';

/**
 * 拉取 NPC 对话渲染上下文（playerName / profileType / cognitiveStyle / stats）。
 *
 * @param npcKey 可选 NPC key，便于后端按 NPC 定制上下文；缺省时取通用上下文。
 */
export async function fetchDialogContext(npcKey?: string): Promise<DialogContextDto> {
  const url = npcKey ? `/api/dialog/context?npcKey=${encodeURIComponent(npcKey)}` : '/api/dialog/context';
  const response = await http.get<DialogContextDto>(url);
  return response.data;
}
