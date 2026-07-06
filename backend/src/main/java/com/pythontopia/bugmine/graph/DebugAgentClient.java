package com.pythontopia.bugmine.graph;

import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.GraphInternalTokenSupport;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class DebugAgentClient {

  private final RestTemplate restTemplate;
  private final GraphServiceProperties properties;

  public DebugAgentClient(RestTemplate graphRestTemplate, GraphServiceProperties properties) {
    this.restTemplate = graphRestTemplate;
    this.properties = properties;
  }

  /** 调用 graph 微服务的 DebugAgent 分析失败提交；服务不可达时抛出 {@link BusinessException}（BAD_GATEWAY）。 */
  public DebugAgentResponse analyse(DebugAgentRequest request) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    GraphInternalTokenSupport.apply(headers, properties);
    try {
      return restTemplate
          .exchange(
              properties.graphUrl() + "/agents/debug/analyze",
              HttpMethod.POST,
              new HttpEntity<>(request, headers),
              DebugAgentResponse.class)
          .getBody();
    } catch (RestClientException exception) {
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY,
          "DEBUG_GRAPH_UNAVAILABLE",
          "Bug 矿洞分析服务暂不可用");
    }
  }
}
