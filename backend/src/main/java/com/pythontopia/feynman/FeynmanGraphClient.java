package com.pythontopia.feynman;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.web.GraphInternalTokenSupport;
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

  /**
   * 多轮教学对话：把该会话此前的轮次作为 {@code history} 一并送给 graph（契约见
   * {@code schemas/feynman.py FeynmanTuteeInput.history}，agent 会把它拼进 messages），
   * 否则 LLM 每轮只看得到最后一句，学生分几轮讲的因果链全部丢失。
   *
   * @param history 按时间正序的历史轮次（不含本轮 studentTeaching）；由调用方裁剪长度
   */
  public Optional<FeynmanOutput> step(
      String kcCode,
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
        new HttpEntity<>(new FeynmanInput(kcCode, wire, studentTeaching, historyWire), headers);
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

  /**
   * 代码找茬：请求学弟写出含 bug 的代码 + 真实 bug 行号（graph /agents/feynman/bug-exposure）。
   * 下游不可达返回 empty，由调用方决定降级或报错。
   */
  public Optional<BugExposure> bugExposure(
      String kcCode, String topic, List<Misconception> misconceptions) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    GraphInternalTokenSupport.apply(headers, properties);
    List<GraphMisconception> wire =
        (misconceptions == null ? List.<Misconception>of() : misconceptions)
            .stream().map(GraphMisconception::from).toList();
    HttpEntity<BugExposureInput> entity =
        new HttpEntity<>(new BugExposureInput(kcCode, topic, wire), headers);
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
      log.warn("feynman bug-exposure graph unavailable: {}", ex.getMessage());
      return Optional.empty();
    }
  }

  /** 请求体：对齐 graph schemas.feynman.FeynmanBugExposureInput（snake_case）。 */
  record BugExposureInput(
      @JsonProperty("kc_code") String kcCode,
      @JsonProperty("topic") String topic,
      @JsonProperty("misconceptions") List<GraphMisconception> misconceptions) {}

  /** graph 代码找茬原始响应（snake_case）；fixed_code 是答案，不进领域/前端。 */
  record GraphBugExposure(
      @JsonProperty("source") String source,
      @JsonProperty("misconception_tag") String misconceptionTag,
      @JsonProperty("buggy_code") String buggyCode,
      @JsonProperty("fixed_code") String fixedCode,
      @JsonProperty("bug_line_indices") List<Integer> bugLineIndices,
      @JsonProperty("hint_md") String hintMd) {
    BugExposure toDomain() {
      return new BugExposure(
          source,
          misconceptionTag,
          buggyCode,
          bugLineIndices == null ? List.of() : bugLineIndices,
          hintMd);
    }
  }

  /** 领域输出：供 FeynmanSessionService 直接消费（不含答案 fixed_code）。 */
  public record BugExposure(
      String source,
      String misconceptionTag,
      String buggyCode,
      List<Integer> bugLineIndices,
      String hintMd) {}

  /** 请求体：对齐 graph schemas.feynman.FeynmanTuteeInput（snake_case）。 */
  record FeynmanInput(
      @JsonProperty("kc_code") String kcCode,
      @JsonProperty("misconceptions") List<GraphMisconception> misconceptions,
      @JsonProperty("student_teaching") String studentTeaching,
      @JsonProperty("history") List<GraphTurn> history) {}

  /**
   * 历史轮次的领域投影。{@code role} 只允许 graph schemas.feynman.Turn 的三个字面量
   * （system/user/assistant），越界会被 pydantic 直接 422。
   */
  public record ConversationTurn(String role, String content) {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
  }

  /** 历史轮次在 graph 线缆上的形态：对齐 schemas.feynman.Turn（role/content）。 */
  record GraphTurn(
      @JsonProperty("role") String role, @JsonProperty("content") String content) {
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
