package com.pythontopia.learner.evidence;

import java.util.List;

public record LearningEvidenceGraphDto(
    Long userId,
    String focusKc,
    List<EvidenceNodeDto> nodes,
    List<EvidenceEdgeDto> edges,
    EvidenceGraphMetricsDto metrics,
    List<EvidencePersonalizationSignalDto> personalizationSignals) {
  public LearningEvidenceGraphDto {
    nodes = nodes == null ? List.of() : List.copyOf(nodes);
    edges = edges == null ? List.of() : List.copyOf(edges);
    personalizationSignals =
        personalizationSignals == null ? List.of() : List.copyOf(personalizationSignals);
  }
}
