package com.pythontopia.bugmine.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record BugMineSessionDto(
    Long id,
    Long userId,
    Long submissionId,
    Long problemId,
    Long questId,
    BugMineSessionStatus status,
    int retryCount,
    int depthLayer,
    OffsetDateTime openedAt,
    OffsetDateTime closedAt,
    BugMineVariantProblemDto variantProblem,
    List<BugMineCardDto> cards) {}
