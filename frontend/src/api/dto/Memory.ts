/**
 * 记忆流（Grounded Generative Agents）只读 DTO。
 *
 * 与后端 `GET /api/learner/memory` 返回的 `MemoryItemDto` 一一对齐（camelCase JSON）。
 * 每条记忆锚定一个真实学习事件（learning_event_log），并携带
 * recency × importance × relevance 的检索打分分解，供「Agent 笔记本」可视化与溯源。
 */
export interface MemoryItemDto {
  /** 来源学习事件 id（learning_event_log.id），用于溯源/防幻觉。 */
  eventId: number;
  /** 事件类型（snake_case），如 `practice_failed` / `resource_consumed`。 */
  eventType: string;
  /** 中文摘要，如「练习失败：PY43-KC-016（WA）」。 */
  summaryZh: string;
  /** 关联知识点编码。 */
  kcCodes: string[];
  /** 事件发生时间（ISO 8601）；历史数据缺失时为 null。 */
  createdAt: string | null;
  /** 写入即定的重要度 1–5（越高越值得反思/检索）。 */
  importance: number;
  /** 近因得分 0–1（半衰期 14 天衰减）。 */
  recencyScore: number;
  /** 重要度归一得分 0–1（= importance / 5）。 */
  importanceScore: number;
  /** 相关性得分 0–1（focusKc 命中 1.0 / 同章 0.5 / 无关 0）。 */
  relevanceScore: number;
  /** 综合得分 0–1（0.4×recency + 0.3×importance + 0.3×relevance）。 */
  totalScore: number;
}
