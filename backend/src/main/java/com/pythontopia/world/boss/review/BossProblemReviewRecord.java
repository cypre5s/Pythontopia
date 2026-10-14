package com.pythontopia.world.boss.review;

import com.pythontopia.world.boss.dto.BossInternalProblem;
import java.time.OffsetDateTime;

/** Backend-only review row. {@code problem} contains hidden tests and must never be returned raw. */
public record BossProblemReviewRecord(
    long id,
    long packId,
    String sourceProblemId,
    String kind,
    BossInternalProblem problem,
    BossProblemReviewDecision decision,
    BossProblemReviewSource reviewSource,
    long requesterId,
    String requesterUsername,
    OffsetDateTime requestedAt,
    Long deciderId,
    String deciderUsername,
    OffsetDateTime decidedAt,
    String decisionReason,
    int version) {}
