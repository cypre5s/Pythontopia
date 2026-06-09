package com.pythontopia.npc.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pythontopia.common.config.GraphServiceProperties;
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
    HttpEntity<NpcDialogGraphRequest> entity =
        new HttpEntity<>(new NpcDialogGraphRequest(npcKey, personaCore, memories, message), headers);
    try {
      NpcDialogResponse body =
          restTemplate
              .exchange(
                  properties.graphUrl() + "/npc/dialog",
                  HttpMethod.POST,
                  entity,
                  NpcDialogResponse.class)
              .getBody();
      return Optional.ofNullable(body);
    } catch (RestClientException ex) {
      log.warn("npc dialog graph unavailable for npcKey={}: {}", npcKey, ex.getMessage());
      return Optional.empty();
    }
  }

  record NpcDialogGraphRequest(
      @JsonProperty("npc_key") String npcKey,
      @JsonProperty("persona_core") String personaCore,
      List<MemoryItemDto> memories,
      @JsonProperty("user_message") String userMessage) {}
}
