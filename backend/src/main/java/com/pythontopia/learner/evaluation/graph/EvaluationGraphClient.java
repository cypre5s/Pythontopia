package com.pythontopia.learner.evaluation.graph;

import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.GraphClientRetryHelper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class EvaluationGraphClient {

  private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

  private final RestTemplate graphRestTemplate;
  private final GraphServiceProperties properties;

  public EvaluationGraphClient(RestTemplate graphRestTemplate, GraphServiceProperties properties) {
    this.graphRestTemplate = graphRestTemplate;
    this.properties = properties;
  }

  public EvaluationNarrativeResponse narrate(EvaluationNarrativeRequest request) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    setInternalToken(headers);
    EvaluationNarrativeResponse response =
        GraphClientRetryHelper.withRetry(
            "evaluation",
            () ->
                graphRestTemplate
                    .exchange(
                        properties.graphUrl() + "/agents/evaluation/narrative",
                        HttpMethod.POST,
                        new HttpEntity<>(request, headers),
                        EvaluationNarrativeResponse.class)
                    .getBody());
    if (response == null) {
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY,
          "EVALUATION_NARRATIVE_EMPTY",
          "EvaluationNarrativeAgent returned an empty response");
    }
    return response;
  }

  private void setInternalToken(HttpHeaders headers) {
    String token = properties.ragAdminToken();
    if (token != null && !token.isBlank()) {
      headers.set(INTERNAL_TOKEN_HEADER, token);
    }
  }
}
