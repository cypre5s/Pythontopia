package com.pythontopia.learner.mastery;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record MasterySnapshot(
    Long id,
    Long userId,
    String kcCode,
    BigDecimal masteryScore,
    BigDecimal confidence,
    OffsetDateTime lastEventAt,
    OffsetDateTime updatedAt) {}
