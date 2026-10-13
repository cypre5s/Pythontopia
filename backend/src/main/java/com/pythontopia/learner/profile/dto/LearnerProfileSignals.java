package com.pythontopia.learner.profile.dto;

import java.util.List;

/**
 * Self-reported goals/preferences and event-backed error hints projected from learner dimensions.
 *
 * <p>Legacy success-rate, resilience and cognitive-style slots remain null for wire compatibility;
 * adaptive decisions use formal mastery and server-side practice history.
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
