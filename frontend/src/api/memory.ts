import { http } from './http';
import type { MemoryItemDto } from './dto/Memory';

/**
 * 拉取「Agent 关于你的笔记本」记忆流 topK。
 *
 * @param focusKc 可选聚焦知识点；不传则后端按 recency+importance 排序。
 * @param limit   可选返回条数；不传则后端默认 8（服务端 clamp 到 1..30）。
 */
export async function getMemory(focusKc?: string, limit?: number): Promise<MemoryItemDto[]> {
  // 仅在有值时带上查询参数，避免向后端发送空串/undefined。
  const params: Record<string, string | number> = {};
  if (focusKc) {
    params.focusKc = focusKc;
  }
  if (limit !== undefined) {
    params.limit = limit;
  }
  const response = await http.get<MemoryItemDto[]>('/api/learner/memory', { params });
  return response.data;
}
