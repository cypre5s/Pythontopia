package com.pythontopia.learner.profile;

import com.pythontopia.learner.profile.dto.DimensionDelta;

/** One validated LearningEvent projection waiting to update a learner profile dimension. */
public record ProfileDimensionProjection(
    Long userId, String dimensionKey, DimensionDelta delta, String idempotencyKey) {}
