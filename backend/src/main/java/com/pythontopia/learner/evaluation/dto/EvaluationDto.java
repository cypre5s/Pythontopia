package com.pythontopia.learner.evaluation.dto;

import com.pythontopia.learner.path.dto.LearningPathDto;
import java.time.OffsetDateTime;
import java.util.List;

public record EvaluationDto(
    Long id,
    Long userId,
    Long packId,
    OffsetDateTime periodStart,
    OffsetDateTime periodEnd,
    String narrativeSummary,
    String pathDiffSummary,
    Long draftPathId,
    Long activePathId,
    OffsetDateTime createdAt,
    EvaluationActedStatus actedStatus,
    List<EvaluationMetricDto> metrics,
    LearningPathDto draftPath) {}
