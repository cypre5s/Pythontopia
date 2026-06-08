package com.pythontopia.bugmine.dto;

import java.math.BigDecimal;

public record BugMineEchoCandidateDto(
    String kcCode,
    Long problemId,
    String problemTitle,
    BigDecimal masteryScore,
    BigDecimal confidence,
    BigDecimal decayRisk,
    BigDecimal minePriority,
    String recommendationReason) {}
