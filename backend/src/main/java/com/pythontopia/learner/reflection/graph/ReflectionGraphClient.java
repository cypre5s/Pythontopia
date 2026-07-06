package com.pythontopia.learner.reflection.graph;

import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.GraphClientRetryHelper;
import java.util.List;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 反思 graph 客户端：POST {@code {graphUrl}/agents/reflection}，把记忆条目交给 ReflectionAgent 综合洞察。
 *
 * <p>fail-fast：超时 / 非 2xx / 反序列化失败统一抛 {@link BusinessException}（BAD_GATEWAY），由
 * {@code ReflectionService} 捕获后走规则兜底，保证演示链路不断。
 */
@Component
public class ReflectionGraphClient {

  private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

  private final RestTemplate restTemplate;
  private final GraphServiceProperties properties;

  public ReflectionGraphClient(RestTemplate graphRestTemplate, GraphServiceProperties properties) {
    this.restTemplate = graphRestTemplate;
    this.properties = properties;
  }

  public ReflectionGraphResponse reflect(Long userId, List<MemoryItemPayload> memories) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    setInternalToken(headers);
    ReflectionGraphRequest request = new ReflectionGraphRequest(userId, memories);
    ReflectionGraphResponse body =
        GraphClientRetryHelper.withRetry(
            "reflection",
            () ->
                restTemplate
                    .exchange(
                        properties.graphUrl() + "/agents/reflection",
                        HttpMethod.POST,
                        new HttpEntity<>(request, headers),
                        ReflectionGraphResponse.class)
                    .getBody());
    if (body == null) {
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY, "REFLECTION_GRAPH_EMPTY", "反思服务返回空响应");
    }
    return body;
  }

  private void setInternalToken(HttpHeaders headers) {
    String token = properties.ragAdminToken();
    if (token != null && !token.isBlank()) {
      headers.set(INTERNAL_TOKEN_HEADER, token);
    }
  }
}
