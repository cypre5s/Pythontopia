package com.pythontopia.learner.path.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record LearningPathDto(
    Long id,
    Long userId,
    Long packId,
    int version,
    LearningPathStatus status,
    String goalSummary,
    OffsetDateTime generatedAt,
    OffsetDateTime nextReviewAt,
    Long sourceRunId,
    List<LearningPathNodeDto> nodes) {}
