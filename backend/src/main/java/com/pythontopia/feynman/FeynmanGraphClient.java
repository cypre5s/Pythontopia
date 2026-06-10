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
    List<GraphMisconception> wire =
        (misconceptions == null ? List.<Misconception>of() : misconceptions)
            .stream().map(GraphMisconception::from).toList();
    HttpEntity<FeynmanInput> entity =
        new HttpEntity<>(new FeynmanInput(kcCode, wire, studentTeaching), headers);
    try {
      GraphFeynmanOutput body =
          restTemplate
              .exchange(
                  // graph 路由统一挂在 /agents 前缀下（见 routes/feynman.py）。
                  properties.graphUrl() + "/agents/feynman/tutee/step",
                  HttpMethod.POST,
                  entity,
                  GraphFeynmanOutput.class)
              .getBody();
      return Optional.ofNullable(body).map(GraphFeynmanOutput::toDomain);
    } catch (RestClientException ex) {
      log.warn("feynman graph unavailable: {}", ex.getMessage());
      return Optional.empty();
    }
  }

  /** 请求体：对齐 graph schemas.feynman.FeynmanTuteeInput（snake_case）。 */
  record FeynmanInput(
      @JsonProperty("kc_code") String kcCode,
      @JsonProperty("misconceptions") List<GraphMisconception> misconceptions,
      @JsonProperty("student_teaching") String studentTeaching) {}

  /** 误解在 graph 线缆上的 snake_case 投影，隔离 wire 格式与领域/DB/前端契约。 */
  record GraphMisconception(
      @JsonProperty("tag") String tag,
      @JsonProperty("evidence_event_ids") List<Long> evidenceEventIds,
      @JsonProperty("resolved") boolean resolved) {
    static GraphMisconception from(Misconception m) {
      return new GraphMisconception(m.tag(), m.evidenceEventIds(), m.resolved());
    }

    Misconception toDomain() {
      return new Misconception(tag, evidenceEventIds, resolved);
    }
  }

  /** graph 原始响应（snake_case）。 */
  record GraphFeynmanOutput(
      @JsonProperty("reply_md") String replyMd,
      @JsonProperty("updated_misconceptions") List<GraphMisconception> updatedMisconceptions,
      @JsonProperty("produced_code") String producedCode) {
    FeynmanOutput toDomain() {
      List<Misconception> updated =
          (updatedMisconceptions == null ? List.<GraphMisconception>of() : updatedMisconceptions)
              .stream().map(GraphMisconception::toDomain).toList();
      return new FeynmanOutput(replyMd, updated, producedCode);
    }
  }

  /** 领域输出：供 FeynmanSessionService 直接消费（misconceptions 用领域类型）。 */
  public record FeynmanOutput(
      String replyMd, List<Misconception> updatedMisconceptions, String producedCode) {}
}
