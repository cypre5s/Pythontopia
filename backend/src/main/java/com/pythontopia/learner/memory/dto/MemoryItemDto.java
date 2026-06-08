package com.pythontopia.learner.memory.dto;

import java.util.List;

/**
 * 「Agent 关于你的笔记本」单条记忆项。除事件本身外，携带 recency/importance/relevance 的分数分解，
 * 供前端透明展示「为什么这条被记住」。
 */
public record MemoryItemDto(
    long eventId,
    String eventType,
    String summaryZh,
    List<String> kcCodes,
    String createdAt,
    int importance,
    double recencyScore,
    double importanceScore,
    double relevanceScore,
    double totalScore) {}
