package com.pythontopia.learner.memory.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Mem0（{@code MEMORY_PROVIDER=mem0}）记忆 provider：对接 Mem0 REST。
 *
 * <ul>
 *   <li>召回：{@code POST {base}/v1/memories/search/}，body {@code {query, user_id, limit}}，解析
 *       {@code results[].{id, memory, score, created_at, metadata:{kc_code, kind}}}。
 *   <li>写入：{@code POST {base}/v1/memories/}，body {@code {messages:[{role,content}], user_id, metadata}}，
 *       取首条 {@code results[].id}（或顶层 {@code id}）为 external_id。
 *   <li>鉴权：{@code Authorization: Token <MEM0_API_KEY>}（缺 key 时不加头，便于自托管开放实例）。
 * </ul>
 *
 * 任意 HTTP 故障经基座包装成 {@link MemoryProviderException}，由 router 回退 local。
 */
@Component
public class Mem0MemoryProvider extends AbstractHttpMemoryProvider {

  private static final String SEARCH_PATH = "/v1/memories/search/";
  private static final String ADD_PATH = "/v1/memories/";

  private final MemoryProviderProperties properties;

  public Mem0MemoryProvider(
      @Qualifier("memoryRestTemplate") RestTemplate memoryRestTemplate,
      ObjectMapper objectMapper,
      MemoryProviderProperties properties) {
    super(memoryRestTemplate, objectMapper);
    this.properties = properties;
  }

  @Override
  public String name() {
    return MemoryProviderProperties.MEM0;
  }

  @Override
  protected String baseUrl() {
    return properties.mem0BaseUrl();
  }

  @Override
  protected void authorize(HttpHeaders headers) {
    String key = properties.mem0ApiKey();
    if (key != null && !key.isBlank()) {
      headers.set(HttpHeaders.AUTHORIZATION, "Token " + key);
    }
  }

  @Override
  public List<RecalledMemory> recall(long userId, String query, int limit) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("query", query == null ? "" : query);
    body.put("user_id", String.valueOf(userId));
    body.put("limit", clampLimit(limit));

    JsonNode root = postForNode(SEARCH_PATH, body);
    JsonNode results = resultsArray(root);
    List<RecalledMemory> out = new ArrayList<>();
    for (JsonNode item : results) {
      String text = textOrNull(item.path("memory"));
      if (text == null) {
        text = textOrNull(item.path("text"));
      }
      if (text == null) {
        continue;
      }
      JsonNode metadata = item.path("metadata");
      out.add(
          new RecalledMemory(
              text,
              textOrNull(metadata.path("kc_code")),
              metadataKind(metadata),
              parseInstant(item.path("created_at")),
              item.path("score").asDouble(0.0),
              name()));
    }
    return out;
  }

  @Override
  public MemoryRef remember(long userId, MemoryEvent event) {
    if (event == null || event.isBlank()) {
      throw new MemoryProviderException("mem0 remember requires non-blank text");
    }
    Map<String, Object> message = new LinkedHashMap<>();
    message.put("role", "user");
    message.put("content", event.text());

    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("kind", event.kind());
    if (event.kcCode() != null && !event.kcCode().isBlank()) {
      metadata.put("kc_code", event.kcCode());
    }

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("messages", List.of(message));
    body.put("user_id", String.valueOf(userId));
    body.put("metadata", metadata);

    JsonNode root = postForNode(ADD_PATH, body);
    String externalId = extractId(root);
    if (externalId == null) {
      throw new MemoryProviderException("mem0 add response missing memory id");
    }
    return new MemoryRef(name(), externalId, event.kind());
  }

  /** Mem0 既可能返回 {@code {results:[...]}} 也可能返回顶层数组，两者都兼容。 */
  private static JsonNode resultsArray(JsonNode root) {
    JsonNode results = root.path("results");
    if (results.isArray()) {
      return results;
    }
    return root.isArray() ? root : MISSING_ARRAY;
  }

  private static String extractId(JsonNode root) {
    JsonNode results = root.path("results");
    if (results.isArray() && results.size() > 0) {
      String id = textOrNull(results.get(0).path("id"));
      if (id != null) {
        return id;
      }
    }
    String topId = textOrNull(root.path("id"));
    if (topId != null) {
      return topId;
    }
    return textOrNull(root.path("memory_id"));
  }

  private static String metadataKind(JsonNode metadata) {
    String kind = textOrNull(metadata.path("kind"));
    return kind == null ? "fact" : kind;
  }

  /** 空数组占位（避免 NPE，等价于无命中）。 */
  private static final JsonNode MISSING_ARRAY =
      new ObjectMapper().createArrayNode();
}
