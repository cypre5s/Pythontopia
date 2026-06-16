package com.pythontopia.intent;

import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.web.GraphClientRetryHelper;
import com.pythontopia.intent.dto.IntentGraphResponse;
import com.pythontopia.intent.dto.IntentRouteRequest;
import com.pythontopia.intent.dto.IntentRouteResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 意图路由的非流式 graph 客户端，镜像 {@code CurriculumMapperClient}：把前端 camelCase 请求转成
 * Python 期望的 snake_case body，POST {@code /agents/intent/route}，再把 snake_case 响应映射回
 * camelCase {@link IntentRouteResponse}。KC 目录由前端透传（前端为 {@code @} 菜单本就全量加载），
 * 本客户端不查库；真正落库的工坊生成端会再次按课程 DB 校验 KC，grounding 完整性不受影响。
 */
@Component
public class IntentGraphClient {

  private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
  private static final Map<String, String> NAMESPACE_BY_INTENT =
      Map.ofEntries(
          Map.entry("ASK", "ask"),
          Map.entry("EXPLAIN_CODE", "ask"),
          Map.entry("FIX_BUG", "ask"),
          Map.entry("ASKME", "quiz"),
          Map.entry("REVIEW", "quiz"),
          Map.entry("HANDOUT", "make"),
          Map.entry("MINDMAP", "make"),
          Map.entry("READING", "make"),
          Map.entry("STORYBOARD", "make"),
          Map.entry("PRACTICE", "practice"),
          Map.entry("CODE_LAB", "practice"),
          Map.entry("PLAN", "plan"),
          Map.entry("PROGRESS", "plan"),
          Map.entry("MY_STUDY", "plan"),
          Map.entry("VIDEO", "find"),
          Map.entry("NAVIGATE", "find"));
  private static final Map<String, String> RISK_CLASS_BY_INTENT =
      Map.ofEntries(
          Map.entry("VIDEO", "read_only"),
          Map.entry("PROGRESS", "read_only"),
          Map.entry("MY_STUDY", "read_only"),
          Map.entry("REVIEW", "navigational"),
          Map.entry("NAVIGATE", "navigational"));
  private static final Map<String, String> RENDER_MODE_BY_INTENT =
      Map.ofEntries(
          Map.entry("HANDOUT", "resource"),
          Map.entry("MINDMAP", "resource"),
          Map.entry("READING", "resource"),
          Map.entry("STORYBOARD", "resource"),
          Map.entry("PRACTICE", "practice"),
          Map.entry("CODE_LAB", "practice"),
          Map.entry("PLAN", "plan"),
          Map.entry("PROGRESS", "progress"),
          Map.entry("NAVIGATE", "navigation"));

  private final RestTemplate restTemplate;
  private final GraphServiceProperties properties;

  public IntentGraphClient(RestTemplate graphRestTemplate, GraphServiceProperties properties) {
    this.restTemplate = graphRestTemplate;
    this.properties = properties;
  }

  /**
   * @param profileBrief 学生 8 维画像摘要（由 {@link IntentService} 注入，前端不传）；空白时不写入 body。
   */
  public IntentRouteResponse route(IntentRouteRequest request, String profileBrief) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("message", request.message());
    if (request.manualKcCodes() != null && !request.manualKcCodes().isEmpty()) {
      body.put("manual_kc_codes", request.manualKcCodes());
    }
    List<Map<String, Object>> courseKcs = buildCourseKcs(request.courseKcs());
    if (!courseKcs.isEmpty()) {
      body.put("course_kcs", courseKcs);
    }
    if (request.currentChapter() != null) {
      body.put("current_chapter", request.currentChapter());
    }
    if (request.recentKcCodes() != null && !request.recentKcCodes().isEmpty()) {
      body.put("recent_kc_codes", request.recentKcCodes());
    }
    // ── R-16 多轮意图理解透传（snake_case 契约见百晓深度优化蓝本 §0）──
    List<Map<String, Object>> recentTurns = buildRecentTurns(request.recentTurns());
    if (!recentTurns.isEmpty()) {
      body.put("recent_turns", recentTurns);
    }
    if (request.lastIntent() != null && !request.lastIntent().isBlank()) {
      body.put("last_intent", request.lastIntent());
    }
    if (request.lastKcCodes() != null && !request.lastKcCodes().isEmpty()) {
      body.put("last_kc_codes", request.lastKcCodes());
    }
    List<Map<String, Object>> corrections = buildSessionCorrections(request.sessionCorrections());
    if (!corrections.isEmpty()) {
      body.put("session_corrections", corrections);
    }
    body.put(
        "scope",
        request.scope() == null || request.scope().isBlank() ? "default" : request.scope());
    body.put(
        "source",
        request.source() == null || request.source().isBlank()
            ? "composer_text"
            : request.source());
    if (request.capabilityNamespace() != null && !request.capabilityNamespace().isBlank()) {
      body.put("capability_namespace", request.capabilityNamespace());
    }
    if (profileBrief != null && !profileBrief.isBlank()) {
      // graph 契约 profile_brief max_length=1000：超长截断，避免极端 tag 长度导致 422 断送路由。
      body.put(
          "profile_brief",
          profileBrief.length() > 1000 ? profileBrief.substring(0, 1000) : profileBrief);
    }

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    setInternalToken(headers);
    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

    IntentGraphResponse graph =
        GraphClientRetryHelper.withRetry(
            "intent",
            () ->
                restTemplate
                    .exchange(
                        properties.graphUrl() + "/agents/intent/route",
                        HttpMethod.POST,
                        entity,
                        IntentGraphResponse.class)
                    .getBody());

    if (graph == null || graph.intent() == null) {
      // graph 异常兜底：回落到答疑，避免前端拿到 null intent。
      return fallbackRouteResponse(request.message());
    }
    return graphRouteResponse(graph);
  }

  private void setInternalToken(HttpHeaders headers) {
    String token = properties.ragAdminToken();
    if (token != null && !token.isBlank()) {
      headers.set(INTERNAL_TOKEN_HEADER, token);
    }
  }

  /** 近期对话轮 → snake_case 条目（role 缺失的脏数据条目直接跳过，不污染 graph 校验）。 */
  private List<Map<String, Object>> buildRecentTurns(List<IntentRouteRequest.Turn> turns) {
    List<Map<String, Object>> payload = new ArrayList<>();
    if (turns == null) {
      return payload;
    }
    for (IntentRouteRequest.Turn turn : turns) {
      if (turn == null || turn.role() == null || turn.role().isBlank()) {
        continue;
      }
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("role", turn.role());
      entry.put("content", turn.content() != null ? turn.content() : "");
      payload.add(entry);
    }
    return payload;
  }

  /** 会话内纠错记忆 → snake_case 条目（corrected_intent 缺失的条目无路由价值，跳过）。 */
  private List<Map<String, Object>> buildSessionCorrections(
      List<IntentRouteRequest.Correction> corrections) {
    List<Map<String, Object>> payload = new ArrayList<>();
    if (corrections == null) {
      return payload;
    }
    for (IntentRouteRequest.Correction correction : corrections) {
      if (correction == null
          || correction.correctedIntent() == null
          || correction.correctedIntent().isBlank()) {
        continue;
      }
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("message", correction.message() != null ? correction.message() : "");
      entry.put("corrected_intent", correction.correctedIntent());
      payload.add(entry);
    }
    return payload;
  }

  private List<Map<String, Object>> buildCourseKcs(List<IntentRouteRequest.CourseKc> kcs) {
    List<Map<String, Object>> payload = new ArrayList<>();
    if (kcs == null) {
      return payload;
    }
    for (IntentRouteRequest.CourseKc kc : kcs) {
      if (kc == null || kc.code() == null || kc.code().isBlank()) {
        continue;
      }
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("code", kc.code());
      entry.put("name", kc.name() != null ? kc.name() : "");
      payload.add(entry);
    }
    return payload;
  }

  private IntentRouteResponse fallbackRouteResponse(String message) {
    return new IntentRouteResponse(
        "ASK",
        List.of(),
        message,
        0.0,
        "graph 无响应",
        "",
        null,
        "ask",
        List.of("ASK"),
        false,
        "",
        "generative",
        "answer",
        "graph_unavailable");
  }

  private IntentRouteResponse graphRouteResponse(IntentGraphResponse graph) {
    String intent = graph.intent();
    return new IntentRouteResponse(
        intent,
        graph.kcCodes() != null ? graph.kcCodes() : List.of(),
        graph.focusHint() != null ? graph.focusHint() : "",
        graph.confidence(),
        graph.reason() != null ? graph.reason() : "",
        graph.target() != null ? graph.target() : "",
        // 无次级意图时 graph 返回 null，保持 null 让前端按「无组合动作」处理。
        graph.secondaryIntent(),
        nonBlank(graph.namespace(), namespaceFor(intent)),
        graph.candidateIntents() != null && !graph.candidateIntents().isEmpty()
            ? graph.candidateIntents()
            : List.of(intent),
        graph.needsClarification() != null && graph.needsClarification(),
        graph.clarificationPrompt() != null ? graph.clarificationPrompt() : "",
        nonBlank(graph.riskClass(), riskClassFor(intent)),
        nonBlank(graph.renderMode(), renderModeFor(intent)),
        nonBlank(graph.reasonCode(), "legacy_response"));
  }

  private String namespaceFor(String intent) {
    return NAMESPACE_BY_INTENT.getOrDefault(intent, "ask");
  }

  private String riskClassFor(String intent) {
    return RISK_CLASS_BY_INTENT.getOrDefault(intent, "generative");
  }

  private String renderModeFor(String intent) {
    return RENDER_MODE_BY_INTENT.getOrDefault(intent, "answer");
  }

  private String nonBlank(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
