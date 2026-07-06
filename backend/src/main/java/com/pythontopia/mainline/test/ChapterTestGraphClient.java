package com.pythontopia.mainline.test;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.web.GraphInternalTokenSupport;
import com.pythontopia.mainline.test.ChapterProblemBankRepository.PreferredCodingQuestion;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * 章末大测试题面生成的<b>类型化 HTTP 客户端</b> → pythontopia-graph {@code POST /agents/chapter-test/generate}
 * （r15 计划 §10，子代理 A2 实现服务端）。
 *
 * <p>请求/响应线缆为 snake_case，隔离于后端领域；任何传输错误 / 非法响应抛
 * {@link ChapterTestGenerationException}（fail-fast）。考试期间零调用——本客户端只在预生成路径使用。
 */
@Component
public class ChapterTestGraphClient {

  private static final Logger log = LoggerFactory.getLogger(ChapterTestGraphClient.class);

  private final RestTemplate restTemplate;
  private final GraphServiceProperties properties;
  private final ObjectMapper objectMapper;

  public ChapterTestGraphClient(
      @Qualifier("chapterTestRestTemplate") RestTemplate graphRestTemplate,
      GraphServiceProperties properties,
      ObjectMapper objectMapper) {
    this.restTemplate = graphRestTemplate;
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  /**
   * 调 graph 生成题面。
   *
   * @return 题面 + 生成审计（generator_meta）。
   * @throws ChapterTestGenerationException 传输失败 / 响应缺 items / items 为空。
   */
  public GeneratedPaper generate(GenerateRequest request) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    GraphInternalTokenSupport.apply(headers, properties);
    HttpEntity<GenerateRequest> entity = new HttpEntity<>(request, headers);
    JsonNode body;
    try {
      body =
          restTemplate
              .exchange(
                  properties.graphUrl() + "/agents/chapter-test/generate",
                  HttpMethod.POST,
                  entity,
                  JsonNode.class)
              .getBody();
    } catch (RestClientException ex) {
      throw new ChapterTestGenerationException("chapter-test graph 调用失败: " + ex.getMessage(), ex);
    }
    JsonNode questionBody =
        body != null && body.path("questions").isObject() ? body.path("questions") : body;
    if (questionBody == null
        || !questionBody.has("items")
        || !questionBody.get("items").isArray()
        || questionBody.get("items").isEmpty()) {
      throw new ChapterTestGenerationException("chapter-test graph 返回无有效 items");
    }
    ObjectNode questions = objectMapper.createObjectNode();
    questions.set("items", questionBody.get("items"));
    return new GeneratedPaper(
        questions,
        buildMeta(body),
        body.has("profile_snapshot") ? body.get("profile_snapshot") : null);
  }

  /** 把 graph 返回的审计字段（run_id/agent/grounding/safety/meta）规整成 generator_meta（防幻觉审计 §10）。 */
  private JsonNode buildMeta(JsonNode body) {
    ObjectNode meta = objectMapper.createObjectNode();
    meta.put("source", "graph");
    meta.put("generated_at", Instant.now().toString());
    if (body.has("generator_meta") && body.get("generator_meta").isObject()) {
      meta.setAll((ObjectNode) body.get("generator_meta"));
      return meta;
    }
    copyIfPresent(body, meta, "run_id");
    copyIfPresent(body, meta, "agent");
    copyIfPresent(body, meta, "grounding");
    copyIfPresent(body, meta, "safety");
    if (body.has("meta") && body.get("meta").isObject()) {
      meta.set("graph_meta", body.get("meta"));
    }
    return meta;
  }

  private void copyIfPresent(JsonNode src, ObjectNode dst, String key) {
    if (src.has(key) && !src.get(key).isNull()) {
      dst.set(key, src.get(key));
    }
  }

  /** 题面 + 生成审计。 */
  public record GeneratedPaper(
      JsonNode questions, JsonNode generatorMeta, JsonNode profileSnapshot) {

    public GeneratedPaper(JsonNode questions, JsonNode generatorMeta) {
      this(questions, generatorMeta, null);
    }
  }

  /** 请求体：对齐 graph schema（snake_case，§10 输入）。 */
  public record GenerateRequest(
      @JsonProperty("user_id") long userId,
      @JsonProperty("chapter") int chapter,
      @JsonProperty("kc_codes") List<String> kcCodes,
      @JsonProperty("profile_brief") JsonNode profileBrief,
      @JsonProperty("errorbook_brief") JsonNode errorbookBrief,
      @JsonProperty("counts") Counts counts,
      @JsonProperty("excluded_questions") List<String> excludedQuestions,
      @JsonProperty("preferred_coding_questions")
          List<PreferredCodingQuestion> preferredCodingQuestions) {

    public record Counts(
        @JsonProperty("choice") int choice,
        @JsonProperty("judge") int judge,
        @JsonProperty("fill") int fill,
        @JsonProperty("coding") int coding) {}
  }
}
