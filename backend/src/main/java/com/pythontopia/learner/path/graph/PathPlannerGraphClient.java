package com.pythontopia.learner.path.graph;

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
public class PathPlannerGraphClient {

  private final RestTemplate restTemplate;
  private final GraphServiceProperties properties;

  public PathPlannerGraphClient(RestTemplate graphRestTemplate, GraphServiceProperties properties) {
    this.restTemplate = graphRestTemplate;
    this.properties = properties;
  }

  public PathPlannerGraphResponse plan(PathPlannerGraphRequest request) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    try {
      return restTemplate
          .exchange(
              properties.graphUrl() + "/agents/path/plan",
              HttpMethod.POST,
              new HttpEntity<>(request, headers),
              PathPlannerGraphResponse.class)
          .getBody();
    } catch (RestClientException exception) {
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY,
          "PATH_GRAPH_UNAVAILABLE",
          "学习路径规划服务暂不可用");
    }
  }
}
