package com.pythontopia.workshop.graph;

import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.error.BusinessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class WorkshopGraphClient {

  private final RestTemplate restTemplate;
  private final GraphServiceProperties properties;

  public WorkshopGraphClient(RestTemplate graphRestTemplate, GraphServiceProperties properties) {
    this.restTemplate = graphRestTemplate;
    this.properties = properties;
  }

  public ResourceProduceResponseDto produce(ResourceProduceRequestDto request) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    try {
      return restTemplate
          .exchange(
              properties.graphUrl() + "/agents/resource/produce",
              HttpMethod.POST,
              new HttpEntity<>(request, headers),
              ResourceProduceResponseDto.class)
          .getBody();
    } catch (RestClientException exception) {
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY,
          "RESOURCE_GRAPH_UNAVAILABLE",
          "资源生成服务暂不可用");
    }
  }
}
