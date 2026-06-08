package com.pythontopia.learner.mastery.review;

import java.time.OffsetDateTime;

/** kc_review_state 一行的快照（FSRS-lite 间隔复习状态）。 */
public record ReviewState(
    Long id,
    Long userId,
    String kcCode,
    double stability,
    double difficulty,
    int reps,
    int lapses,
    OffsetDateTime lastReviewAt,
    OffsetDateTime dueAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
