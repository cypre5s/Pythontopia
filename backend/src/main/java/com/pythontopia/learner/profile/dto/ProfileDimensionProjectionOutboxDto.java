package com.pythontopia.learner.profile.dto;

import java.time.OffsetDateTime;

/** Safe operational metadata for an admin; projection payload and idempotency key are omitted. */
public record ProfileDimensionProjectionOutboxDto(
    long id,
    long userId,
    String dimensionKey,
    String status,
    int attemptCount,
    String errorKind,
    int manualReplayCount,
    Long lastReplayedBy,
    OffsetDateTime lastReplayedAt,
    OffsetDateTime createdAt,
    OffsetDateTime failedAt,
    OffsetDateTime nextAttemptAt,
    OffsetDateTime processedAt) {}
