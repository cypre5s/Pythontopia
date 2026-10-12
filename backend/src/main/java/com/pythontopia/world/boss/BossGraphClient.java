package com.pythontopia.world.boss;

import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.web.GraphClientRetryHelper;
import com.pythontopia.world.boss.dto.BossEvaluateResultDto;
import com.pythontopia.world.boss.dto.BossInternalProblem;
import com.pythontopia.world.boss.dto.BossProblemRequest.ProfileSignalsPayload;
import com.pythontopia.world.boss.dto.BossTestCasePayload;
import com.pythontopia.world.boss.dto.FrontierSelectResultDto;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * 与 pythontopia-graph 服务通信，请求 boss 题目与评测。
 *
 * <p>graph 端点：
 *
 * <ul>
 *   <li>{@code POST /agents/boss/problem} → {@link BossInternalProblem}
 *   <li>{@code POST /agents/boss/evaluate} → {@link BossEvaluateResultDto}
 * </ul>
 *
 * <p>本类只负责 HTTP 编排与失败兜底，不实现题目生成或评测。
 */
@Component
public class BossGraphClient {

  private static final Logger LOG = LoggerFactory.getLogger(BossGraphClient.class);
  private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

  private final RestTemplate restTemplate;
  private final RestTemplate bossProblemRestTemplate;
  private final GraphServiceProperties properties;

  public BossGraphClient(
      @Qualifier("graphRestTemplate") RestTemplate graphRestTemplate,
      @Qualifier("bossProblemRestTemplate") RestTemplate bossProblemRestTemplate,
      GraphServiceProperties properties) {
    this.restTemplate = graphRestTemplate;
    this.bossProblemRestTemplate = bossProblemRestTemplate;
    this.properties = properties;
  }

  /**
   * 调 graph 出题（不带画像参数的重载）。失败抛 {@link RestClientException}，由调用方决定是否降级。
   *
   * <p>preferredKc / preferredKind / profileSignals 透传给 graph，由 PracticeAgent 决定是否走画像偏好模板。
   */
  public BossInternalProblem requestProblem(
      Long userId, int chapter, int stage, String kcCode, Integer difficulty) {
    return requestProblem(userId, chapter, stage, kcCode, difficulty, null, null, null);
  }

  /** 完整重载：前端透传 profile signals 给 graph 供画像偏好出题。 */
  public BossInternalProblem requestProblem(
      Long userId,
      int chapter,
      int stage,
      String kcCode,
      Integer difficulty,
      String preferredKc,
      String preferredKind,
      ProfileSignalsPayload profileSignals) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    Map<String, Object> body = new HashMap<>();
    body.put("userId", userId == null ? -1L : userId);
    body.put("chapter", chapter);
    body.put("stage", stage);
    body.put("kcCode", kcCode == null ? "" : kcCode);
    body.put("difficulty", difficulty == null ? Math.min(5, Math.max(1, stage)) : difficulty);
    if (preferredKc != null) {
      body.put("preferredKc", preferredKc);
    }
    if (preferredKind != null) {
      body.put("preferredKind", preferredKind);
    }
    if (profileSignals != null) {
      Map<String, Object> sig = new HashMap<>();
      if (profileSignals.weakKcCodes() != null) sig.put("weakKcCodes", profileSignals.weakKcCodes());
      if (profileSignals.pythonSelfRatedLevel() != null)
        sig.put("pythonSelfRatedLevel", profileSignals.pythonSelfRatedLevel());
      if (profileSignals.successRate() != null) sig.put("successRate", profileSignals.successRate());
      if (profileSignals.mediaPriority() != null) sig.put("mediaPriority", profileSignals.mediaPriority());
      if (profileSignals.resilience() != null) sig.put("resilience", profileSignals.resilience());
      if (profileSignals.questPreferredKind() != null)
        sig.put("questPreferredKind", profileSignals.questPreferredKind());
      if (!sig.isEmpty()) {
        body.put("profileSignals", sig);
      }
    }
    setInternalToken(headers);
    return GraphClientRetryHelper.withRetry(
        "boss-problem",
        () ->
            bossProblemRestTemplate
                .exchange(
                    properties.graphUrl() + "/agents/boss/problem",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    BossInternalProblem.class)
                .getBody());
  }

  /**
   * 调 graph 知识前沿选择器（无尽远征生成地图用）。失败抛 {@link RestClientException}，由调用方决定降级。
   *
   * <p>mastery 与 confidence 由后端从权威来源读取后传入；profileSignals / recentKcs / focusChapter / exclude 透传。
   * lastPracticed 为每个 KC 的后端权威最近练习时间（ISO-8601，来自正式练习事件），
   * 供 graph 做真·间隔重复（久未练上浮），见 {@code expedition_selector.select_frontier}。
   */
  public FrontierSelectResultDto selectFrontier(
      Map<String, Double> mastery,
      Map<String, Double> confidence,
      ProfileSignalsPayload profileSignals,
      List<String> recentKcs,
      Integer focusChapter,
      List<String> exclude,
      int count,
      Map<String, String> lastPracticed) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    setInternalToken(headers);
    List<Map<String, Object>> masteryList =
        mastery.entrySet().stream()
            .<Map<String, Object>>map(
                e -> {
                  Map<String, Object> entry = new HashMap<>();
                  entry.put("kcCode", e.getKey());
                  entry.put("masteryScore", e.getValue());
                  if (confidence != null && confidence.containsKey(e.getKey())) {
                    entry.put("confidence", confidence.get(e.getKey()));
                  }
                  return entry;
                })
            .toList();
    Map<String, Object> body = new HashMap<>();
    body.put("mastery", masteryList);
    body.put("recentKcs", recentKcs == null ? List.of() : recentKcs);
    body.put("lastPracticed", lastPracticed == null ? Map.of() : lastPracticed);
    body.put("exclude", exclude == null ? List.of() : exclude);
    body.put("count", count);
    if (focusChapter != null) {
      body.put("focusChapter", focusChapter);
    }
    Map<String, Object> sig = signalsToMap(profileSignals);
    if (!sig.isEmpty()) {
      body.put("profileSignals", sig);
    }
    return GraphClientRetryHelper.withRetry(
        "boss-select-frontier",
        () ->
            restTemplate
                .exchange(
                    properties.graphUrl() + "/agents/boss/select-frontier",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    FrontierSelectResultDto.class)
                .getBody());
  }

  private static Map<String, Object> signalsToMap(ProfileSignalsPayload profileSignals) {
    Map<String, Object> sig = new HashMap<>();
    if (profileSignals == null) {
      return sig;
    }
    if (profileSignals.weakKcCodes() != null) sig.put("weakKcCodes", profileSignals.weakKcCodes());
    if (profileSignals.pythonSelfRatedLevel() != null)
      sig.put("pythonSelfRatedLevel", profileSignals.pythonSelfRatedLevel());
    if (profileSignals.successRate() != null) sig.put("successRate", profileSignals.successRate());
    if (profileSignals.mediaPriority() != null)
      sig.put("mediaPriority", profileSignals.mediaPriority());
    if (profileSignals.resilience() != null) sig.put("resilience", profileSignals.resilience());
    if (profileSignals.questPreferredKind() != null)
      sig.put("questPreferredKind", profileSignals.questPreferredKind());
    return sig;
  }

  /** 调 graph 评测玩家提交的代码。失败抛 {@link RestClientException}。 */
  public BossEvaluateResultDto evaluateAttempt(
      String problemId, String code, List<BossTestCasePayload> testCases, int timeoutSec) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    setInternalToken(headers);
    List<Map<String, Object>> cases =
        testCases.stream()
            .<Map<String, Object>>map(
                tc ->
                    Map.of(
                        "input", tc.input(),
                        "expectedOutput", tc.expectedOutput(),
                        "isSample", tc.isSample()))
            .toList();
    Map<String, Object> body = new HashMap<>();
    body.put("problemId", problemId);
    body.put("code", code);
    body.put("testCases", cases);
    body.put("timeoutSec", timeoutSec);
    return GraphClientRetryHelper.withRetry(
        "boss-evaluate",
        () ->
            restTemplate
                .exchange(
                    properties.graphUrl() + "/agents/boss/evaluate",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    BossEvaluateResultDto.class)
                .getBody());
  }

  /** 用于运维探活。失败返回 false，永不抛。 */
  public boolean healthy() {
    try {
      restTemplate.getForObject(properties.graphUrl() + "/healthz", String.class);
      return true;
    } catch (RestClientException ex) {
      LOG.debug("graph boss health failed: {}", ex.getMessage());
      return false;
    }
  }

  private void setInternalToken(HttpHeaders headers) {
    String token = properties.ragAdminToken();
    if (token != null && !token.isBlank()) {
      headers.set(INTERNAL_TOKEN_HEADER, token);
    }
  }
}
