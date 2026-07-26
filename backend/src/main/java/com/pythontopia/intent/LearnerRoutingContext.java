package com.pythontopia.intent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务端聚合的百晓工坊个性化事实。浏览器不得声明这些字段；Intent/Orchestrator 在调用 Graph
 * 前按当前用户重新构建。
 */
public record LearnerRoutingContext(
    String profileBrief,
    Map<String, Double> kcMastery,
    Map<String, Double> kcConfidence,
    List<String> weakKcs,
    List<String> recentErrorKcs,
    String cognitiveLoad,
    String frustrationLevel,
    String cognitiveStyle,
    List<String> learningPreferences,
    String profileMajor,
    int pythonLevel,
    List<RecentResource> recentResources,
    List<RecentSubmission> recentSubmissions,
    List<String> strugglePatterns,
    List<String> recentWins,
    List<String> interventionHints,
    String asOf) {

  public LearnerRoutingContext {
    profileBrief = profileBrief == null ? "" : profileBrief;
    kcMastery = kcMastery == null ? Map.of() : Map.copyOf(kcMastery);
    kcConfidence = kcConfidence == null ? Map.of() : Map.copyOf(kcConfidence);
    weakKcs = immutable(weakKcs);
    recentErrorKcs = immutable(recentErrorKcs);
    cognitiveLoad = level(cognitiveLoad);
    frustrationLevel = level(frustrationLevel);
    cognitiveStyle = cognitiveStyle == null ? "" : cognitiveStyle;
    learningPreferences = immutable(learningPreferences);
    profileMajor = profileMajor == null ? "OTHER" : profileMajor;
    pythonLevel = Math.max(1, Math.min(5, pythonLevel));
    recentResources = recentResources == null ? List.of() : List.copyOf(recentResources);
    recentSubmissions = recentSubmissions == null ? List.of() : List.copyOf(recentSubmissions);
    strugglePatterns = immutable(strugglePatterns);
    recentWins = immutable(recentWins);
    interventionHints = immutable(interventionHints);
    asOf = asOf == null ? "" : asOf;
  }

  public static LearnerRoutingContext empty() {
    return new LearnerRoutingContext(
        "",
        Map.of(),
        Map.of(),
        List.of(),
        List.of(),
        "LOW",
        "LOW",
        "",
        List.of(),
        "OTHER",
        2,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        "");
  }

  /** Graph 使用 snake_case；集中在 DTO 内转换，避免两个客户端各维护一套字段。 */
  public Map<String, Object> toGraphPayload() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("profile_brief", profileBrief);
    payload.put("kc_mastery", kcMastery);
    payload.put("kc_confidence", kcConfidence);
    payload.put("weak_kcs", weakKcs);
    payload.put("recent_error_kcs", recentErrorKcs);
    payload.put("cognitive_load", cognitiveLoad);
    payload.put("frustration_level", frustrationLevel);
    payload.put("cognitive_style", cognitiveStyle);
    payload.put("learning_preferences", learningPreferences);
    payload.put("profile_major", profileMajor);
    payload.put("python_level", pythonLevel);
    payload.put(
        "recent_resources", recentResources.stream().map(RecentResource::toGraphPayload).toList());
    payload.put(
        "recent_submissions",
        recentSubmissions.stream().map(RecentSubmission::toGraphPayload).toList());
    payload.put("struggle_patterns", strugglePatterns);
    payload.put("recent_wins", recentWins);
    payload.put("intervention_hints", interventionHints);
    payload.put("as_of", asOf);
    return payload;
  }

  private static List<String> immutable(List<String> values) {
    return values == null ? List.of() : List.copyOf(values);
  }

  private static String level(String value) {
    return "HIGH".equals(value) || "MEDIUM".equals(value) ? value : "LOW";
  }

  public record RecentResource(String kcCode, String type, String createdAt) {
    Map<String, Object> toGraphPayload() {
      return Map.of(
          "kc_code", kcCode == null ? "" : kcCode,
          "type", type == null ? "HANDOUT" : type,
          "created_at", createdAt == null ? "" : createdAt);
    }
  }

  public record RecentSubmission(Long problemId, String kcCode, boolean passed) {
    Map<String, Object> toGraphPayload() {
      return Map.of(
          "problem_id", problemId == null ? 0L : problemId,
          "kc_code", kcCode == null ? "" : kcCode,
          "passed", passed);
    }
  }
}
