package com.pythontopia.learner.evidence;

import java.util.List;

public record EvidencePersonalizationSignalDto(
    String kcCode,
    int failedChecks,
    int passedChecks,
    int failedPracticeEvents,
    int groundedPageCount,
    List<Long> evidenceEventIds,
    List<Long> checkIds,
    List<Long> pageIds,
    double priorityScore,
    String reason) {
  public EvidencePersonalizationSignalDto {
    evidenceEventIds = evidenceEventIds == null ? List.of() : List.copyOf(evidenceEventIds);
    checkIds = checkIds == null ? List.of() : List.copyOf(checkIds);
    pageIds = pageIds == null ? List.of() : List.copyOf(pageIds);
  }
}
