package com.pythontopia.learner.mastery.dto;

import java.math.BigDecimal;

public record MasterySnapshotDto(
    String kcCode,
    BigDecimal masteryScore,
    BigDecimal confidence,
    String lastEventAt,
    String latestSource) {}
