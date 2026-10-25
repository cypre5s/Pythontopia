package com.pythontopia.npc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.learner.memory.dto.MemoryItemDto;
import com.pythontopia.npc.dto.NpcDialogResponse;
import com.pythontopia.npc.graph.NpcDialogGraphClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** NPC 对话服务：回忆该 NPC 视角的记忆，调用 graph 个性化回复，失败时静态脚本兜底。 */
@Service("memoryNpcDialogService")
public class NpcDialogService {

  private static final double TALK_AFFINITY_DELTA = 0.05;

  private final NpcMemoryService memoryService;
  private final NpcPersonaService personaService;
  private final NpcAffinityService affinityService;
  private final NpcDialogGraphClient graphClient;
  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;
  private final TransactionTemplate transaction;

  public NpcDialogService(
      NpcMemoryService memoryService,
      NpcPersonaService personaService,
      NpcAffinityService affinityService,
      NpcDialogGraphClient graphClient,
      JdbcTemplate jdbc,
      ObjectMapper objectMapper,
      PlatformTransactionManager transactionManager) {
    this.memoryService = memoryService;
    this.personaService = personaService;
    this.affinityService = affinityService;
    this.graphClient = graphClient;
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
    this.transaction = new TransactionTemplate(transactionManager);
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public NpcDialogResponse talk(long userId, String npcKey, String message) {
    List<MemoryItemDto> memories = memoryService.recall(userId, npcKey, null, 5);
    String personaCore = loadPersonaCore(npcKey);
    NpcDialogResponse response =
        graphClient
            .talk(npcKey, personaCore, memories, message)
            .orElseGet(() -> staticFallback(npcKey));
    transaction.execute(
        status -> {
          persistDialogState(userId, npcKey, response);
          affinityService.bump(userId, npcKey, TALK_AFFINITY_DELTA);
          return null;
        });
    return response;
  }

  private String loadPersonaCore(String npcKey) {
    try {
      return personaService.load(npcKey).coreMd();
    } catch (RuntimeException ex) {
      return "- NPC：" + npcKey + "\n- 风格：温和、具体、教育优先。\n";
    }
  }

  private void persistDialogState(long userId, String npcKey, NpcDialogResponse response) {
    jdbc.update(
        "INSERT INTO npc_dialog_state (user_id, npc_key, last_talked_at, flags) "
            + "VALUES (?, ?, now(), jsonb_build_object('cited_event_ids', ?::jsonb, 'grounded', ?)) "
            + "ON CONFLICT (user_id, npc_key) DO UPDATE SET last_talked_at = now(), "
            + "flags = EXCLUDED.flags, updated_at = now()",
        userId,
        npcKey,
        toJson(response.citedEventIds()),
        response.grounded());
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      return "[]";
    }
  }

  private NpcDialogResponse staticFallback(String npcKey) {
    String text = loadStaticGreeting(npcKey);
    return new NpcDialogResponse(text, List.of(), false);
  }

  private String loadStaticGreeting(String npcKey) {
    Path path = resolveNpcRoot().resolve(npcKey).resolve("dialog.json");
    if (!Files.isRegularFile(path)) {
      return "我先按旧脚本陪你练，等下游服务恢复后再读取你的长期记忆。";
    }
    try {
      JsonNode root = objectMapper.readTree(path.toFile());
      String rootNodeId = root.path("rootNodeId").asText("greet");
      String text = root.path("nodes").path(rootNodeId).path("text").asText("");
      return text.isBlank()
          ? "我先按旧脚本陪你练，等下游服务恢复后再读取你的长期记忆。"
          : text.replace("{{playerName}}", "同学");
    } catch (IOException ex) {
      return "我先按旧脚本陪你练，等下游服务恢复后再读取你的长期记忆。";
    }
  }

  private static Path resolveNpcRoot() {
    Path repoRoot = Path.of("data", "npcs");
    if (Files.isDirectory(repoRoot)) {
      return repoRoot;
    }
    return Path.of("..", "data", "npcs");
  }
}
