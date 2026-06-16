package com.pythontopia.npc.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.web.GraphInternalTokenSupport;
import com.pythontopia.learner.memory.dto.MemoryItemDto;
import com.pythontopia.npc.dto.NpcDialogResponse;
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

/** 调用 graph 的 NPC 对话 agent；不可用时返回 Optional.empty，让 service 静态兜底。 */
@Component
public class NpcDialogGraphClient {

  private static final Logger log = LoggerFactory.getLogger(NpcDialogGraphClient.class);

  private final RestTemplate restTemplate;
  private final GraphServiceProperties properties;

  public NpcDialogGraphClient(RestTemplate graphRestTemplate, GraphServiceProperties properties) {
    this.restTemplate = graphRestTemplate;
    this.properties = properties;
  }

  public Optional<NpcDialogResponse> talk(
      String npcKey, String personaCore, List<MemoryItemDto> memories, String message) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    // 内部调用令牌：与 IntentGraphClient / RagEmbeddingClient 一致复用 ragAdminToken；
    // 缺省（未配 PYT_RAG_ADMIN_TOKEN）时不写头，配置后避免寒暄/对话恒 401。
    GraphInternalTokenSupport.apply(headers, properties);
    List<RecalledMemoryPayload> projected =
        (memories == null ? List.<MemoryItemDto>of() : memories).stream()
            .map(RecalledMemoryPayload::from)
            .toList();
    HttpEntity<NpcDialogGraphRequest> entity =
        new HttpEntity<>(
            new NpcDialogGraphRequest(npcKey, personaCore, projected, message), headers);
    try {
      NpcDialogGraphResponse body =
          restTemplate
              .exchange(
                  // graph 路由统一挂在 /agents 前缀下（见 routes/npc_dialog.py）。
                  properties.graphUrl() + "/agents/npc/dialog",
                  HttpMethod.POST,
                  entity,
                  NpcDialogGraphResponse.class)
              .getBody();
      return Optional.ofNullable(body).map(NpcDialogGraphResponse::toDomain);
    } catch (RestClientException ex) {
      log.warn("npc dialog graph unavailable for npcKey={}: {}", npcKey, ex.getMessage());
      return Optional.empty();
    }
  }

  /** 请求体：字段对齐 graph schemas.npc_dialog.NpcDialogInput（snake_case）。 */
  record NpcDialogGraphRequest(
      @JsonProperty("npc_key") String npcKey,
      @JsonProperty("persona_core") String personaCore,
      @JsonProperty("memories") List<RecalledMemoryPayload> memories,
      @JsonProperty("user_message") String userMessage) {}

  /** 记忆投影：对齐 graph schemas.npc_dialog.RecalledMemory（event_id 必填，summary/kc_codes）。 */
  record RecalledMemoryPayload(
      @JsonProperty("event_id") long eventId,
      @JsonProperty("summary") String summary,
      @JsonProperty("kc_codes") List<String> kcCodes) {
    static RecalledMemoryPayload from(MemoryItemDto memory) {
      return new RecalledMemoryPayload(
          memory.eventId(),
          memory.summaryZh() == null ? "" : memory.summaryZh(),
          memory.kcCodes() == null ? List.of() : memory.kcCodes());
    }
  }

  /** 响应体：对齐 graph schemas.npc_dialog.NpcDialogOutput（snake_case）。 */
  record NpcDialogGraphResponse(
      @JsonProperty("reply_md") String replyMd,
      @JsonProperty("cited_event_ids") List<Long> citedEventIds,
      @JsonProperty("grounded") boolean grounded) {
    NpcDialogResponse toDomain() {
      return new NpcDialogResponse(replyMd, citedEventIds, grounded);
    }
  }
}
