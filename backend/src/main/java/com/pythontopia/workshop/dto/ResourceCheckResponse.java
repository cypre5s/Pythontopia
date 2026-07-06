package com.pythontopia.workshop.dto;

import java.math.BigDecimal;

public record ResourceCheckResponse(
    Long checkId,
    Long resourceId,
    String kcCode,
    ResourceCheckStatus status,
    BigDecimal masteryDelta,
    String nextAction,
    ResourceType remediationType,
    String feedback,
    String loopState,
    Long practiceSubmissionId,
    String decisionReason) {}
