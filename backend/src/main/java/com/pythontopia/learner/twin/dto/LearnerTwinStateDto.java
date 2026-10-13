package com.pythontopia.learner.twin.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** Read-only learner state assembled from course, verified mastery, and review records. */
public record LearnerTwinStateDto(
    int assessedKcCount,
    int totalKcCount,
    int masteredKcCount,
    String growthStage,
    List<KcProgress> kcs,
    List<NextStep> nextSteps) {

  public LearnerTwinStateDto {
    kcs = kcs == null ? List.of() : List.copyOf(kcs);
    nextSteps = nextSteps == null ? List.of() : List.copyOf(nextSteps);
  }

  public record KcProgress(
      String kcCode,
      String kcName,
      int chapter,
      String chapterName,
      boolean assessed,
      Double masteryScore,
      Double confidence,
      boolean reviewDue,
      OffsetDateTime dueAt,
      OffsetDateTime lastPracticedAt,
      int consecutiveFailures) {}

  public record NextStep(
      String kcCode,
      String kcName,
      int chapter,
      String chapterName,
      int difficulty,
      String action,
      String reason,
      boolean remediationRequired) {}
}
