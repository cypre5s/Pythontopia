package com.pythontopia.feynman;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pythontopia.common.config.GraphServiceProperties;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class FeynmanGraphClient {

  private static final Logger log = LoggerFactory.getLogger(FeynmanGraphClient.class);

  private final RestTemplate restTemplate;
  private final GraphServiceProperties properties;

  public FeynmanGraphClient(RestTemplate graphRestTemplate, GraphServiceProperties properties) {
    this.restTemplate = graphRestTemplate;
    this.properties = properties;
  }

  public Optional<FeynmanOutput> step(
      String kcCode, List<Misconception> misconceptions, String studentTeaching) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<FeynmanInput> entity =
        new HttpEntity<>(new FeynmanInput(kcCode, misconceptions, studentTeaching), headers);
    try {
      return Optional.ofNullable(
          restTemplate
              .exchange(
                  properties.graphUrl() + "/feynman/step",
                  HttpMethod.POST,
                  entity,
                  FeynmanOutput.class)
              .getBody());
    } catch (RestClientException ex) {
      log.warn("feynman graph unavailable: {}", ex.getMessage());
      return Optional.empty();
    }
  }

  record FeynmanInput(
      @JsonProperty("kc_code") String kcCode,
      List<Misconception> misconceptions,
      @JsonProperty("student_teaching") String studentTeaching) {}

  public record FeynmanOutput(
      @JsonProperty("reply_md") String replyMd,
      @JsonProperty("updated_misconceptions") List<Misconception> updatedMisconceptions,
      @JsonProperty("produced_code") String producedCode) {}
}
