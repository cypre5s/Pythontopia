package com.pythontopia.learner.memory.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * 外部 HTTP 记忆 provider（Mem0 / Zep）公共基座：统一 POST JSON、统一鉴权头、统一把任意 HTTP 故障
 * （不可达 / 超时 / 4xx / 5xx / 响应不可解析 / 配置缺失）包装成 {@link MemoryProviderException}，交由
 * {@link MemoryProviderRouter} fail-soft 回退 local。子类只关注端点路径、请求体与响应字段映射。
 */
abstract class AbstractHttpMemoryProvider implements MemoryProvider {

  /** 召回条数安全区间（防止外部参数越界或拖慢）。 */
  protected static final int MIN_LIMIT = 1;

  protected static final int MAX_LIMIT = 50;

  private final Logger log = LoggerFactory.getLogger(getClass());

  protected final RestTemplate restTemplate;
  protected final ObjectMapper objectMapper;

  protected AbstractHttpMemoryProvider(RestTemplate restTemplate, ObjectMapper objectMapper) {
    this.restTemplate = restTemplate;
    this.objectMapper = objectMapper;
  }

  /** 子类提供已去尾斜杠的 base URL；空白表示未配置。 */
  protected abstract String baseUrl();

  /** 子类填充鉴权头（如 {@code Authorization: Token <key>}）。 */
  protected abstract void authorize(HttpHeaders headers);

  /** base URL 缺失即视为故障 → 抛出触发回退，避免向 {@code null/} 发请求。 */
  protected void requireConfigured() {
    if (baseUrl() == null || baseUrl().isBlank()) {
      throw new MemoryProviderException(name() + " base url is not configured (set env)");
    }
  }

  /**
   * POST JSON 到 {@code baseUrl()+path}，返回响应 JSON 根节点。任意 {@link RestClientException}
   * （含超时/5xx/4xx）或空响应 → {@link MemoryProviderException}。
   */
  protected JsonNode postForNode(String path, Map<String, Object> body) {
    requireConfigured();
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
    authorize(headers);
    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
    try {
      JsonNode node =
          restTemplate
              .exchange(baseUrl() + path, HttpMethod.POST, entity, JsonNode.class)
              .getBody();
      if (node == null || node.isNull() || node.isMissingNode()) {
        throw new MemoryProviderException(name() + " returned empty body for " + path);
      }
      return node;
    } catch (RestClientException ex) {
      log.warn("{} HTTP 调用失败 path={}: {}", name(), path, ex.getMessage());
      throw new MemoryProviderException(name() + " HTTP call failed: " + ex.getMessage(), ex);
    }
  }

  protected static int clampLimit(int limit) {
    if (limit < MIN_LIMIT) {
      return MIN_LIMIT;
    }
    return Math.min(limit, MAX_LIMIT);
  }

  /** 解析 ISO-8601 时间戳；缺失 / 不可解析 → {@code null}（不阻断召回）。 */
  protected static Instant parseInstant(JsonNode node) {
    if (node == null || node.isNull() || !node.isTextual()) {
      return null;
    }
    String text = node.asText();
    if (text.isBlank()) {
      return null;
    }
    try {
      return Instant.parse(text);
    } catch (DateTimeParseException ex) {
      return null;
    }
  }

  protected static String textOrNull(JsonNode node) {
    return node == null || node.isNull() || !node.isTextual() || node.asText().isBlank()
        ? null
        : node.asText();
  }
}
