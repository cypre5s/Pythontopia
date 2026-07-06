package com.pythontopia.learner.path.graph;

import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.web.GraphClientRetryHelper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class PathPlannerGraphClient {

  private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

  private final RestTemplate restTemplate;
  private final GraphServiceProperties properties;

  public PathPlannerGraphClient(RestTemplate graphRestTemplate, GraphServiceProperties properties) {
    this.restTemplate = graphRestTemplate;
    this.properties = properties;
  }

  public PathPlannerGraphResponse plan(PathPlannerGraphRequest request) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    setInternalToken(headers);
    return GraphClientRetryHelper.withRetry(
        "path-planner",
        () ->
            restTemplate
                .exchange(
                    properties.graphUrl() + "/agents/path/plan",
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    PathPlannerGraphResponse.class)
                .getBody());
  }

  private void setInternalToken(HttpHeaders headers) {
    String token = properties.ragAdminToken();
    if (token != null && !token.isBlank()) {
      headers.set(INTERNAL_TOKEN_HEADER, token);
    }
  }
}
