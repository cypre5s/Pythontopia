package com.pythontopia.workshop.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ResourceProduceRequestDto(
    @JsonProperty("user_id") Long userId,
    @JsonProperty("kc_codes") List<String> kcCodes,
    String type,
    @JsonProperty("focus_hint") String focusHint,
    @JsonProperty("profile_snapshot") ProfileSnapshotDto profileSnapshot,
    @JsonProperty("target_difficulty") String targetDifficulty,
    // P4.1 自我进化闭环：后端把目标 agent 的最新 agent_strategy 折算成 directive 下传，闭合「反馈→策略→生成」。
    // PINNED snake_case key（graph lane 新增可选字段消费）；无策略时为 null。
    @JsonProperty("agent_strategy_directive") String agentStrategyDirective) {

  public record ProfileSnapshotDto(
      String major,
      @JsonProperty("python_self_rated_level") int pythonSelfRatedLevel,
      @JsonProperty("media_priority") List<String> mediaPriority,
      @JsonProperty("cognitive_style") String cognitiveStyle,
      @JsonProperty("error_patterns") List<String> errorPatterns,
      @JsonProperty("recent_insight") String recentInsight,
      @JsonProperty("memory_highlights") List<String> memoryHighlights,
      @JsonProperty("memory_directives") List<String> memoryDirectives) {}
}
