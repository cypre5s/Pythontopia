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
 * Zep（{@code MEMORY_PROVIDER=zep}）记忆 provider：对接 Zep 时序知识图谱 REST，天然契合 brief 的「时序」诉求。
 *
 * <ul>
 *   <li>召回：{@code POST {base}/api/v2/graph/search}，body {@code {user_id, query, limit, scope:"edges"}}，
 *       解析 {@code edges[].{fact, created_at|valid_at, score, attributes:{kc_code}}}（时序事实优先）。
 *   <li>写入：{@code POST {base}/api/v2/graph}，body {@code {user_id, type:"text", data}}，取 {@code uuid}（或
 *       {@code episode_uuid} / {@code id}）为 external_id。
 *   <li>鉴权：{@code Authorization: Api-Key <ZEP_API_KEY>}。
 * </ul>
 *
 * 任意 HTTP 故障经基座包装成 {@link MemoryProviderException}，由 router 回退 local。
 */
@Component
public class ZepMemoryProvider extends AbstractHttpMemoryProvider {

  private static final String SEARCH_PATH = "/api/v2/graph/search";
  private static final String ADD_PATH = "/api/v2/graph";

  private final MemoryProviderProperties properties;

  public ZepMemoryProvider(
      @Qualifier("memoryRestTemplate") RestTemplate memoryRestTemplate,
      ObjectMapper objectMapper,
      MemoryProviderProperties properties) {
    super(memoryRestTemplate, objectMapper);
    this.properties = properties;
  }

  @Override
  public String name() {
    return MemoryProviderProperties.ZEP;
  }

  @Override
  protected String baseUrl() {
    return properties.zepBaseUrl();
  }

  @Override
  protected void authorize(HttpHeaders headers) {
    String key = properties.zepApiKey();
    if (key != null && !key.isBlank()) {
      headers.set(HttpHeaders.AUTHORIZATION, "Api-Key " + key);
    }
  }

  @Override
  public List<RecalledMemory> recall(long userId, String query, int limit) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("user_id", String.valueOf(userId));
    body.put("query", query == null ? "" : query);
    body.put("limit", clampLimit(limit));
    body.put("scope", "edges");

    JsonNode root = postForNode(SEARCH_PATH, body);
    JsonNode edges = edgeArray(root);
    List<RecalledMemory> out = new ArrayList<>();
    for (JsonNode edge : edges) {
      String fact = textOrNull(edge.path("fact"));
      if (fact == null) {
        fact = textOrNull(edge.path("content"));
      }
      if (fact == null) {
        continue;
      }
      out.add(
          new RecalledMemory(
              fact,
              textOrNull(edge.path("attributes").path("kc_code")),
              "fact",
              firstNonNullInstant(edge),
              edge.path("score").asDouble(0.0),
              name()));
    }
    return out;
  }

  @Override
  public MemoryRef remember(long userId, MemoryEvent event) {
    if (event == null || event.isBlank()) {
      throw new MemoryProviderException("zep remember requires non-blank text");
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("user_id", String.valueOf(userId));
    body.put("type", "text");
    body.put("data", event.text());

    JsonNode root = postForNode(ADD_PATH, body);
    String externalId = extractId(root);
    if (externalId == null) {
      throw new MemoryProviderException("zep add response missing uuid");
    }
    return new MemoryRef(name(), externalId, event.kind());
  }

  /** Zep 时序边的有效时间优先取 {@code valid_at}，回退 {@code created_at}。 */
  private static java.time.Instant firstNonNullInstant(JsonNode edge) {
    java.time.Instant validAt = parseInstant(edge.path("valid_at"));
    return validAt != null ? validAt : parseInstant(edge.path("created_at"));
  }

  private static JsonNode edgeArray(JsonNode root) {
    JsonNode edges = root.path("edges");
    if (edges.isArray()) {
      return edges;
    }
    JsonNode results = root.path("results");
    if (results.isArray()) {
      return results;
    }
    return root.isArray() ? root : MISSING_ARRAY;
  }

  private static String extractId(JsonNode root) {
    String uuid = textOrNull(root.path("uuid"));
    if (uuid != null) {
      return uuid;
    }
    String episode = textOrNull(root.path("episode_uuid"));
    if (episode != null) {
      return episode;
    }
    return textOrNull(root.path("id"));
  }

  private static final JsonNode MISSING_ARRAY = new ObjectMapper().createArrayNode();
}
