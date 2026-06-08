package com.pythontopia.learner.reflection.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * graph ReflectionAgent 产出的单条洞察（与 graph {@code schemas/reflection.Insight} 对齐）。
 * 规则兜底（{@code ReflectionService.ruleFallback}）也复用此结构，便于统一回写/落库。
 */
public record InsightPayload(
    String text,
    @JsonProperty("dimension_key") String dimensionKey,
    List<String> tags,
    @JsonProperty("evidence_event_ids") List<Long> evidenceEventIds,
    double confidence) {

  public List<String> safeTags() {
    return tags == null ? List.of() : tags;
  }

  public List<Long> safeEvidence() {
    return evidenceEventIds == null ? List.of() : evidenceEventIds;
  }
}
