package com.pythontopia.learner.evaluation.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record EvaluationNarrativeRequest(
    @JsonProperty("user_id") Long userId,
    @JsonProperty("pack_id") Long packId,
    List<Metric> metrics,
    @JsonProperty("top_kcs") List<KcSummary> topKcs,
    @JsonProperty("recent_events") List<EventSummary> recentEvents,
    @JsonProperty("profile_brief") ProfileBrief profileBrief) {

  public record Metric(
      @JsonProperty("metric_key") String metricKey,
      double numerator,
      double denominator,
      double value,
      @JsonProperty("display_unit") String displayUnit,
      String notes) {}

  public record KcSummary(
      @JsonProperty("kc_code") String kcCode,
      @JsonProperty("kc_name") String kcName,
      @JsonProperty("mastery_score") double masteryScore,
      @JsonProperty("delta_score") double deltaScore) {}

  public record EventSummary(
      @JsonProperty("event_kind") String eventKind,
      @JsonProperty("occurred_at") String occurredAt,
      String detail) {}

  public record ProfileBrief(
      String major,
      @JsonProperty("python_level") int pythonLevel,
      @JsonProperty("has_recent_failures") boolean hasRecentFailures) {}
}
