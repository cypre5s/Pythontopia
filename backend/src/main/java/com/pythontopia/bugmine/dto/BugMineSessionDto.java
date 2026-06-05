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
    int profileDepthTarget,
    boolean masteryConfirmed,
    Long variantSubmissionId,
    OffsetDateTime openedAt,
    OffsetDateTime closedAt,
    BugMineVariantProblemDto variantProblem,
    String profileGuidance,
    List<BugMineDepthStepDto> depthSteps,
    List<BugMineCardDto> cards) {}
