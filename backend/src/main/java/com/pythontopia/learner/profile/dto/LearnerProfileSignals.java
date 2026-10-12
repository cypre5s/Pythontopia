package com.pythontopia.learner.profile.dto;

import java.util.List;

/**
 * Low-risk personalization inputs projected from the unified learner dimensions.
 *
 * <p>Values such as success rate, resilience, and self-rated level are snapshots from interview
 * metadata, not live outcome statistics. Adaptive decisions must continue to use formal mastery and
 * practice history as their behavioral evidence.
 */
public record LearnerProfileSignals(
    List<String> frequentKcCodes,
    Integer pythonSelfRatedLevel,
    Double successRate,
    List<String> mediaPriority,
    Double resilience,
    Double avgAttemptsPerProblem,
    String major,
    String learningGoalKind,
    List<String> targetKcCodes,
    Double fieldDependent,
    Double sequentialVsHolistic,
    Integer sessionMinutesPerDay) {

  public LearnerProfileSignals {
    frequentKcCodes = frequentKcCodes == null ? List.of() : List.copyOf(frequentKcCodes);
    mediaPriority = mediaPriority == null ? List.of() : List.copyOf(mediaPriority);
    targetKcCodes = targetKcCodes == null ? List.of() : List.copyOf(targetKcCodes);
  }

  public static LearnerProfileSignals empty() {
    return new LearnerProfileSignals(
        List.of(), null, null, List.of(), null, null, null, null, List.of(), null, null, null);
  }
}
