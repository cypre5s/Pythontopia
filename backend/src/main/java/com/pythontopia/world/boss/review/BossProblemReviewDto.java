package com.pythontopia.world.boss.review;

import com.pythontopia.world.boss.dto.BossProblemDto;
import java.time.OffsetDateTime;

/** Public reviewer projection. Hidden cases/reference solutions never enter this DTO. */
public record BossProblemReviewDto(
    long id,
    long packId,
    String sourceProblemId,
    String kind,
    BossProblemDto problem,
    BossProblemReviewDecision decision,
    long requesterId,
    String requesterUsername,
    OffsetDateTime requestedAt,
    Long deciderId,
    String deciderUsername,
    OffsetDateTime decidedAt,
    String decisionReason,
    int version) {}
