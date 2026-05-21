package com.pythontopia.learner.dto;

public record LearnerProfileDto(
    Long profileId,
    Long userId,
    Long packId,
    String major,
    String learningGoal,
    String cognitiveStyle,
    String narrativeSummary) {}
