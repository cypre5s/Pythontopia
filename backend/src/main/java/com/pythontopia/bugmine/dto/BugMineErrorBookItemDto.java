package com.pythontopia.bugmine.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record BugMineErrorBookItemDto(
    Long sessionId,
    Long submissionId,
    Long problemId,
    Long questId,
    String kcCode,
    String problemTitle,
    BugMineSessionStatus status,
    int retryCount,
    int depthLayer,
    int profileDepthTarget,
    String profileGuidance,
    BigDecimal masteryScore,
    BigDecimal minePriority,
    BigDecimal decayRisk,
    String recommendationReason,
    List<String> mistakePatterns,
    boolean patternMatched,
    OffsetDateTime openedAt,
    BugMineVariantProblemDto variantProblem,
    List<BugMineDepthStepDto> depthSteps) {}
