package com.pythontopia.workshop.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ResourceProduceRequestDto(
    @JsonProperty("user_id") Long userId,
    @JsonProperty("kc_codes") List<String> kcCodes,
    String type,
    @JsonProperty("focus_hint") String focusHint,
    @JsonProperty("profile_snapshot") ProfileSnapshotDto profileSnapshot) {

  public record ProfileSnapshotDto(
      String major,
      @JsonProperty("python_self_rated_level") int pythonSelfRatedLevel,
      @JsonProperty("media_priority") List<String> mediaPriority,
      @JsonProperty("cognitive_style") String cognitiveStyle,
      @JsonProperty("error_patterns") List<String> errorPatterns,
      @JsonProperty("recent_insight") String recentInsight,
      @JsonProperty("memory_highlights") List<String> memoryHighlights) {}
}
