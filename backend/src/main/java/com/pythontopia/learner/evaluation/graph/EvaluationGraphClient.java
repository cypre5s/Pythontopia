package com.pythontopia.learner.evaluation.graph;

import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.error.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class EvaluationGraphClient {

  private final RestTemplate graphRestTemplate;
  private final GraphServiceProperties properties;

  public EvaluationGraphClient(RestTemplate graphRestTemplate, GraphServiceProperties properties) {
    this.graphRestTemplate = graphRestTemplate;
    this.properties = properties;
  }

  public EvaluationNarrativeResponse narrate(EvaluationNarrativeRequest request) {
    try {
      EvaluationNarrativeResponse response =
          graphRestTemplate.postForObject(
              properties.graphUrl() + "/agents/evaluation/narrative",
              request,
              EvaluationNarrativeResponse.class);
      if (response == null) {
        throw new BusinessException(
            HttpStatus.BAD_GATEWAY,
            "EVALUATION_NARRATIVE_EMPTY",
            "EvaluationNarrativeAgent returned an empty response");
      }
      return response;
    } catch (RestClientException ex) {
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY,
          "EVALUATION_NARRATIVE_UNAVAILABLE",
          "EvaluationNarrativeAgent is unavailable: " + ex.getMessage());
    }
  }
}
