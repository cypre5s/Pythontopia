package com.pythontopia.browse.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.GraphInternalTokenSupport;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * 「联网查证」(BROWSE) 的 graph 客户端：POST graph 的 {@code /agents/browse/run}，把服务端注入的白名单
 * （{@code allowed_domains}）、目标与有界步数交给 graph 侧 executor（B1 落地）执行只读浏览，回带步骤与
 * 命中 url。
 *
 * <p><b>不重试</b>：BROWSE 非幂等且有浏览器副作用（registry {@code idempotent=false, max_retries=0}），
 * 故传输失败时仅做一次调用并直接抛 {@link BusinessException}(502)——经 {@code GlobalExceptionHandler}
 * 统一成 {@code ApiError}，绝不冒泡成 500，也绝不重复发起浏览。
 */
@Component
public class BrowseGraphClient {

  private static final Logger log = LoggerFactory.getLogger(BrowseGraphClient.class);

  private final RestTemplate restTemplate;
  private final GraphServiceProperties properties;

  public BrowseGraphClient(RestTemplate graphRestTemplate, GraphServiceProperties properties) {
    this.restTemplate = graphRestTemplate;
    this.properties = properties;
  }

  /**
   * 调 graph 执行一次有界浏览。
   *
   * @param goal 学生目标（自然语言）
   * @param allowedDomains 服务端注入的安全墙白名单（不接收前端）
   * @param maxSteps 有界步数上限
   * @param userId 发起人（graph 侧按此隔离 / 限流）
   * @return 浏览结果（业务 fail-fast 时 {@code error} 非空、{@code steps} 含已走部分）
   * @throws BusinessException 502 当 graph 不可达 / HTTP 错误 / 空响应
   */
  public BrowseGraphResult browse(
      String goal, List<String> allowedDomains, int maxSteps, long userId) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    GraphInternalTokenSupport.apply(headers, properties);
    GraphBrowseRequest payload =
        new GraphBrowseRequest(
            goal, allowedDomains == null ? List.of() : allowedDomains, maxSteps, userId);
    try {
      GraphBrowseResponse body =
          restTemplate
              .exchange(
                  properties.graphUrl() + "/agents/browse/run",
                  HttpMethod.POST,
                  new HttpEntity<>(payload, headers),
                  GraphBrowseResponse.class)
              .getBody();
      if (body == null) {
        throw unavailable("联网查证服务返回空响应");
      }
      return body.toResult();
    } catch (RestClientException ex) {
      log.warn("BROWSE graph call failed (no retry, non-idempotent): {}", ex.getMessage());
      throw unavailable("联网查证服务暂不可用");
    }
  }

  private BusinessException unavailable(String message) {
    return new BusinessException(HttpStatus.BAD_GATEWAY, "BROWSE_GRAPH_UNAVAILABLE", message);
  }

  /** 请求体：对齐 {@code tools/browse/contract.py:BrowseRequest}（snake_case）。 */
  record GraphBrowseRequest(
      @JsonProperty("goal") String goal,
      @JsonProperty("allowed_domains") List<String> allowedDomains,
      @JsonProperty("max_steps") int maxSteps,
      @JsonProperty("user_id") long userId) {}

  /** 响应体：对齐 {@code tools/browse/contract.py:BrowseResult}（snake_case）。 */
  record GraphBrowseResponse(
      @JsonProperty("steps") List<GraphBrowseStep> steps,
      @JsonProperty("final_summary") String finalSummary,
      @JsonProperty("citations") List<String> citations,
      @JsonProperty("truncated") boolean truncated,
      @JsonProperty("error") String error) {

    BrowseGraphResult toResult() {
      List<BrowseGraphStep> mapped =
          (steps == null ? List.<GraphBrowseStep>of() : steps).stream()
              .map(GraphBrowseStep::toStep)
              .toList();
      return new BrowseGraphResult(mapped, finalSummary, citations, truncated, error);
    }
  }

  /** 步骤项：对齐 {@code BrowseStep}（snake_case）。 */
  record GraphBrowseStep(
      @JsonProperty("index") int index,
      @JsonProperty("action") String action,
      @JsonProperty("url") String url,
      @JsonProperty("summary") String summary,
      @JsonProperty("title") String title,
      @JsonProperty("snippet") String snippet) {

    BrowseGraphStep toStep() {
      String normalizedSummary = summary == null ? "" : summary;
      String normalizedSnippet = snippet == null || snippet.isBlank() ? normalizedSummary : snippet;
      if (normalizedSummary.isBlank()) {
        normalizedSummary = normalizedSnippet;
      }
      return new BrowseGraphStep(
          index,
          action == null ? "" : action,
          url == null ? "" : url,
          normalizedSummary,
          title == null ? "" : title,
          normalizedSnippet);
    }
  }
}
