package com.pythontopia.learner.recommendation;

public record RecommendationDto(
    Long resourceId,
    String title,
    String type,
    String kcCode,
    String kcName,
    String reason) {}
