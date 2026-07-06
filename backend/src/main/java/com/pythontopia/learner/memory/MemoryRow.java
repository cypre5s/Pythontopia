package com.pythontopia.learner.memory;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

/**
 * 记忆流中一条已结构化的事件行（来自 {@code learning_event_log}），供 {@link MemoryScorer} 打分与
 * {@link MemoryRetrievalService} 组装中文摘要。
 *
 * @param eventId 事件主键
 * @param eventType 规范 snake_case 事件类型（对齐 {@code EventImportance} key）
 * @param kcCodes 从 payload 抽取的相关 KC code（可能为空）
 * @param importance 写入时确定的重要度 1–5
 * @param createdAt 事件发生时间
 * @param payload 原始事件载荷（可空；用于生成摘要细节，如 failureKind/resourceKind）
 */
public record MemoryRow(
    long eventId,
    String eventType,
    List<String> kcCodes,
    int importance,
    Instant createdAt,
    JsonNode payload) {

  public MemoryRow {
    kcCodes = kcCodes == null ? List.of() : List.copyOf(kcCodes);
  }
}
