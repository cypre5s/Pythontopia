package com.pythontopia.world.boss.review;

import java.util.List;

/** Stable page envelope for reviewer queue search results. */
public record BossProblemReviewPageDto(
    List<BossProblemReviewDto> content,
    int page,
    int size,
    long totalElements,
    int totalPages) {}
