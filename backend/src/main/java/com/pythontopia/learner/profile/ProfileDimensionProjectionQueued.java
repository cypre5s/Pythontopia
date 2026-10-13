package com.pythontopia.learner.profile;

/** Internal signal dispatched after the outbox row commits. */
public record ProfileDimensionProjectionQueued(long outboxId) {}
