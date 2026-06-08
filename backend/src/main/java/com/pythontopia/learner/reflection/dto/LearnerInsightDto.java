package com.pythontopia.learner.reflection.dto;

import java.util.List;

/**
 * 一条学情洞察（反思产物）。{@code evidenceEventIds} 指向 {@code learning_event_log.id}，前端可据此回放证据，
 * 满足「每条洞察可溯源」的防幻觉红线。
 */
public record LearnerInsightDto(
    long id,
    String text,
    String dimensionKey,
    List<String> tags,
    List<Long> evidenceEventIds,
    double confidence,
    String source,
    String createdAt) {}
