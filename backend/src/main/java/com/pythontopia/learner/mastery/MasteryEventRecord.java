package com.pythontopia.learner.mastery;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record MasteryEventRecord(
    Long id,
    Long userId,
    String kcCode,
    MasterySource source,
    BigDecimal delta,
    String reason,
    OffsetDateTime createdAt) {}
