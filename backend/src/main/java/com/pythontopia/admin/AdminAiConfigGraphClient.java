package com.pythontopia.admin;

import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.GraphInternalTokenSupport;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class AdminAiConfigGraphClient {
  private final RestTemplate restTemplate;
  private final GraphServiceProperties properties;

  public AdminAiConfigGraphClient(
      @Qualifier("graphRestTemplate") RestTemplate restTemplate, GraphServiceProperties properties) {
    this.restTemplate = restTemplate;
    this.properties = properties;
  }

  public GraphStatus status() { return exchange(HttpMethod.GET, null, GraphStatus.class, ""); }
  public GraphStatus apply(String model, String apiKey) { return exchange(HttpMethod.PUT, new GraphConfigRequest(model, apiKey), GraphStatus.class, ""); }
  public GraphTestResult test(String model, String apiKey) { return exchange(HttpMethod.POST, new GraphConfigRequest(model, apiKey), GraphTestResult.class, "/test"); }

  private <T> T exchange(HttpMethod method, Object body, Class<T> type, String pathSuffix) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    GraphInternalTokenSupport.apply(headers, properties);
    try {
      ResponseEntity<T> response = restTemplate.exchange(properties.graphUrl() + "/internal/admin/llm-config" + pathSuffix, method, new HttpEntity<>(body, headers), type);
      return response.getBody();
    } catch (RestClientException exception) {
      throw new BusinessException(org.springframework.http.HttpStatus.BAD_GATEWAY, "AI_CONFIG_SERVICE_UNAVAILABLE", "AI 配置服务暂不可用，请确认 Graph 服务与内部令牌配置");
    }
  }

  public record GraphConfigRequest(String model, String apiKey) {}
  public record GraphStatus(String provider, String model, boolean apiKeyConfigured) {}
  public record GraphTestResult(boolean success, String model, String message) {}
}
