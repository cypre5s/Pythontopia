package com.pythontopia.feynman;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.web.GraphInternalTokenSupport;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
  private final RestTemplate tuteeRestTemplate;
  private final GraphServiceProperties properties;

  @Autowired
  public FeynmanGraphClient(
      @Qualifier("graphRestTemplate") RestTemplate graphRestTemplate,
      @Qualifier("feynmanTuteeRestTemplate") RestTemplate feynmanTuteeRestTemplate,
      GraphServiceProperties properties) {
    this.restTemplate = graphRestTemplate;
    this.tuteeRestTemplate = feynmanTuteeRestTemplate;
    this.properties = properties;
  }

  FeynmanGraphClient(RestTemplate graphRestTemplate, GraphServiceProperties properties) {
    this(graphRestTemplate, graphRestTemplate, properties);
  }

  public Optional<FeynmanOutput> step(
      String kcCode,
      String topic,
      LearnerContext learnerContext,
      List<Misconception> misconceptions,
      String studentTeaching,
      List<ConversationTurn> history) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    // 内部调用令牌：与 IntentGraphClient / RagEmbeddingClient 一致复用 ragAdminToken；
    // 缺省时不写头，配置 PYT_RAG_ADMIN_TOKEN 后避免费曼对话恒 401。
    GraphInternalTokenSupport.apply(headers, properties);
    List<GraphMisconception> wire =
        (misconceptions == null ? List.<Misconception>of() : misconceptions)
            .stream().map(GraphMisconception::from).toList();
    List<GraphTurn> historyWire =
        (history == null ? List.<ConversationTurn>of() : history)
            .stream().map(GraphTurn::from).toList();
    HttpEntity<FeynmanInput> entity =
        new HttpEntity<>(
            new FeynmanInput(kcCode, topic, learnerContext, wire, studentTeaching, historyWire),
            headers);
    try {
      GraphFeynmanOutput body =
          tuteeRestTemplate
              .exchange(
                  // graph 路由统一挂在 /agents 前缀下（见 routes/feynman.py）。
                  properties.graphUrl() + "/agents/feynman/tutee/step",
                  HttpMethod.POST,
                  entity,
                  GraphFeynmanOutput.class)
              .getBody();
      return Optional.ofNullable(body).map(GraphFeynmanOutput::toDomain);
    } catch (RestClientException ex) {
      log.warn("feynman graph unavailable: {}", ex.getClass().getSimpleName());
      return Optional.empty();
    }
  }

  /**
   * 代码找茬：请求学弟写出含 bug 的代码 + 真实 bug 行号（graph /agents/feynman/bug-exposure）。 下游不可达返回
   * empty，由调用方决定降级或报错。
   */
  public Optional<BugExposure> bugExposure(
      String kcCode,
      String topic,
      LearnerContext learnerContext,
      List<Misconception> misconceptions) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    GraphInternalTokenSupport.apply(headers, properties);
    List<GraphMisconception> wire =
        (misconceptions == null ? List.<Misconception>of() : misconceptions)
            .stream().map(GraphMisconception::from).toList();
    HttpEntity<BugExposureInput> entity =
        new HttpEntity<>(new BugExposureInput(kcCode, topic, learnerContext, wire), headers);
    try {
      GraphBugExposure body =
          restTemplate
              .exchange(
                  properties.graphUrl() + "/agents/feynman/bug-exposure",
                  HttpMethod.POST,
                  entity,
                  GraphBugExposure.class)
              .getBody();
      return Optional.ofNullable(body).map(GraphBugExposure::toDomain);
    } catch (RestClientException ex) {
      log.warn("feynman bug-exposure graph unavailable: {}", ex.getClass().getSimpleName());
      return Optional.empty();
    }
  }

  /** 请求体：对齐 graph schemas.feynman.FeynmanBugExposureInput（snake_case）。 */
  record BugExposureInput(
      @JsonProperty("kc_code") String kcCode,
      @JsonProperty("topic") String topic,
      @JsonProperty("learner_context") LearnerContext learnerContext,
      @JsonProperty("misconceptions") List<GraphMisconception> misconceptions) {}

  /** graph 代码找茬原始响应（snake_case）；fixed_code 是答案，不进领域/前端。 */
  record GraphBugExposure(
      @JsonProperty("source") String source,
      @JsonProperty("misconception_tag") String misconceptionTag,
      @JsonProperty("buggy_code") String buggyCode,
      @JsonProperty("fixed_code") String fixedCode,
      @JsonProperty("bug_line_indices") List<Integer> bugLineIndices,
      @JsonProperty("hint_md") String hintMd,
      @JsonProperty("difficulty") String difficulty,
      @JsonProperty("generated_by_ai") boolean generatedByAi) {
    BugExposure toDomain() {
      return new BugExposure(
          source,
          misconceptionTag,
          buggyCode,
          fixedCode,
          bugLineIndices == null ? List.of() : bugLineIndices,
          hintMd,
          difficulty,
          generatedByAi);
    }
  }

  /** 领域输出：供 FeynmanSessionService 直接消费（不含答案 fixed_code）。 */
  public record BugExposure(
      String source,
      String misconceptionTag,
      String buggyCode,
      String fixedCode,
      List<Integer> bugLineIndices,
      String hintMd,
      String difficulty,
      boolean generatedByAi) {}

  /** 请求体：对齐 graph schemas.feynman.FeynmanTuteeInput（snake_case）。 */
  record FeynmanInput(
      @JsonProperty("kc_code") String kcCode,
      @JsonProperty("topic") String topic,
      @JsonProperty("learner_context") LearnerContext learnerContext,
      @JsonProperty("misconceptions") List<GraphMisconception> misconceptions,
      @JsonProperty("student_teaching") String studentTeaching,
      @JsonProperty("history") List<GraphTurn> history) {}

  public record ConversationTurn(String role, String content) {}

  public record LearnerContext(
      String level, @JsonProperty("response_style") String responseStyle) {}

  record GraphTurn(@JsonProperty("role") String role, @JsonProperty("content") String content) {
    static GraphTurn from(ConversationTurn turn) {
      return new GraphTurn(turn.role(), turn.content());
    }
  }

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
