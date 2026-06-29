package com.pythontopia.learner.memory.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 记忆 brief（镜像 §3.2 Python {@code MemoryBrief}，供 graph 侧 {@code brief_client.py} 注入
 * {@code OrchestratorRequest.context["memory_brief"]}）。
 *
 * <p>JSON 字段强制 snake_case 与冻结契约一致：{@code facts / weak_kcs / style_notes / as_of}。
 *
 * @param facts 时序压缩后的稳定事实（外部召回 / 本地记忆，按近→远排序）
 * @param weakKcs 近期反复受阻 / 到期需复习的 KC code
 * @param styleNotes 认知风格 / 偏好 / 情绪提示（来自 8 维画像）
 * @param asOf 生成时间 ISO-8601（体现「截至此刻」的时序语义）
 * @param stablePreferences 类 ChatGPT memory 的稳定偏好摘要
 * @param strugglePatterns 近期反复卡住的模式
 * @param recentWins 近期成功与逆袭事实，用于正向迁移
 * @param interventionHints 下一轮导学/资源生成可直接使用的干预提示
 */
public record MemoryBriefDto(
    @JsonProperty("facts") List<String> facts,
    @JsonProperty("weak_kcs") List<String> weakKcs,
    @JsonProperty("style_notes") List<String> styleNotes,
    @JsonProperty("as_of") String asOf,
    @JsonProperty("stable_preferences") List<String> stablePreferences,
    @JsonProperty("struggle_patterns") List<String> strugglePatterns,
    @JsonProperty("recent_wins") List<String> recentWins,
    @JsonProperty("intervention_hints") List<String> interventionHints) {

  public MemoryBriefDto {
    facts = facts == null ? List.of() : List.copyOf(facts);
    weakKcs = weakKcs == null ? List.of() : List.copyOf(weakKcs);
    styleNotes = styleNotes == null ? List.of() : List.copyOf(styleNotes);
    stablePreferences = stablePreferences == null ? List.of() : List.copyOf(stablePreferences);
    strugglePatterns = strugglePatterns == null ? List.of() : List.copyOf(strugglePatterns);
    recentWins = recentWins == null ? List.of() : List.copyOf(recentWins);
    interventionHints = interventionHints == null ? List.of() : List.copyOf(interventionHints);
  }

  public MemoryBriefDto(
      List<String> facts, List<String> weakKcs, List<String> styleNotes, String asOf) {
    this(facts, weakKcs, styleNotes, asOf, List.of(), List.of(), List.of(), List.of());
  }
}
