package com.pythontopia.workshop.dto;

import java.math.BigDecimal;
import java.util.List;

public record ResourceEffectivenessDto(
    Long resourceId,
    ResourceFeedbackStatus feedbackStatus,
    int adoptedCount,
    int rejectedCount,
    int checkCount,
    int passedCheckCount,
    int failedCheckCount,
    BigDecimal passRate,
    BigDecimal masteryDeltaSum,
    List<Long> evidencePageIds,
    String signal,
    String evaluationHook) {}
