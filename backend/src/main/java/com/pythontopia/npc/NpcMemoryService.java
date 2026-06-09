package com.pythontopia.npc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.learner.memory.MemoryEmbeddingRepository;
import com.pythontopia.learner.memory.dto.MemoryItemDto;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** (user, npc) 作用域的 NPC 记忆关联服务。 */
@Service
public class NpcMemoryService {

  private static final double REMEMBER_AFFINITY_DELTA = 0.05;

  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;
  private final MemoryEmbeddingRepository memoryEmbeddingRepository;
  private final NpcAffinityService affinityService;

  public NpcMemoryService(
      JdbcTemplate jdbc,
      ObjectMapper objectMapper,
      MemoryEmbeddingRepository memoryEmbeddingRepository,
      NpcAffinityService affinityService) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
    this.memoryEmbeddingRepository = memoryEmbeddingRepository;
    this.affinityService = affinityService;
  }

  @Transactional
  public void remember(long userId, String npcKey, long eventId, double salience) {
    if (bumpNearDuplicate(userId, npcKey, eventId)) {
      affinityService.bump(userId, npcKey, REMEMBER_AFFINITY_DELTA);
      return;
    }
    jdbc.update(
        "INSERT INTO npc_memory (user_id, npc_key, event_id, salience) VALUES (?, ?, ?, ?) "
            + "ON CONFLICT (user_id, npc_key, event_id) DO UPDATE SET "
            + "salience = LEAST(1.0, npc_memory.salience + EXCLUDED.salience)",
        userId,
        npcKey,
        eventId,
        clamp01(salience));
    affinityService.bump(userId, npcKey, REMEMBER_AFFINITY_DELTA);
  }

  @Transactional(readOnly = true)
  public List<MemoryItemDto> recall(long userId, String npcKey, String focusKc, int limit) {
    return jdbc.query(
        "SELECT le.id, le.event_type, le.payload::text AS payload_text, le.importance, "
            + "le.created_at, nm.salience "
            + "FROM npc_memory nm "
            + "JOIN learning_event_log le ON le.id = nm.event_id "
            + "WHERE nm.user_id = ? AND nm.npc_key = ? AND nm.superseded_by IS NULL "
            + "ORDER BY nm.salience DESC, nm.created_at DESC "
            + "LIMIT ?",
        (rs, rowNum) -> {
          long eventId = rs.getLong("id");
          String eventType = rs.getString("event_type");
          JsonNode payload = parsePayload(rs.getString("payload_text"));
          Timestamp ts = rs.getTimestamp("created_at");
          Instant createdAt = ts == null ? null : ts.toInstant();
          double salience = rs.getDouble("salience");
          List<String> kcCodes = extractKcCodes(payload);
          return new MemoryItemDto(
              eventId,
              eventType,
              summary(eventType, payload, kcCodes),
              kcCodes,
              createdAt == null ? null : createdAt.toString(),
              rs.getInt("importance"),
              0.0,
              0.0,
              round(salience),
              round(salience));
        },
        userId,
        npcKey,
        Math.max(1, Math.min(30, limit)));
  }

  @Transactional
  public void supersede(long oldMemoryId, long newMemoryId) {
    jdbc.update("UPDATE npc_memory SET superseded_by = ? WHERE id = ?", newMemoryId, oldMemoryId);
  }

  private boolean bumpNearDuplicate(long userId, String npcKey, long eventId) {
    return memoryEmbeddingRepository
        .find(eventId)
        .map(
            current -> {
              List<Long> existing =
                  jdbc.query(
                      "SELECT event_id FROM npc_memory "
                          + "WHERE user_id = ? AND npc_key = ? AND superseded_by IS NULL "
                          + "ORDER BY created_at DESC LIMIT 30",
                      (rs, rowNum) -> rs.getLong("event_id"),
                      userId,
                      npcKey);
              for (Long existingEventId : existing) {
                if (memoryEmbeddingRepository
                    .find(existingEventId)
                    .map(
                        vector ->
                            NpcMemoryDedup.isNearDuplicate(
                                current, vector, NpcMemoryDedup.DEFAULT_THRESHOLD))
                    .orElse(false)) {
                  jdbc.update(
                      "UPDATE npc_memory SET salience = LEAST(1.0, salience + 0.1), "
                          + "last_recalled_at = now() "
                          + "WHERE user_id = ? AND npc_key = ? AND event_id = ?",
                      userId,
                      npcKey,
                      existingEventId);
                  return true;
                }
              }
              return false;
            })
        .orElse(false);
  }

  private JsonNode parsePayload(String payloadText) {
    if (payloadText == null || payloadText.isBlank()) {
      return objectMapper.createObjectNode();
    }
    try {
      return objectMapper.readTree(payloadText);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      return objectMapper.createObjectNode();
    }
  }

  private static List<String> extractKcCodes(JsonNode payload) {
    JsonNode single = payload == null ? null : payload.get("kcCode");
    if (single != null && single.isTextual() && !single.asText("").isBlank()) {
      return List.of(single.asText(""));
    }
    return List.of();
  }

  private static String summary(String eventType, JsonNode payload, List<String> kcCodes) {
    String kc = kcCodes.isEmpty() ? "未关联知识点" : kcCodes.get(0);
    return switch (eventType) {
      case "practice_failed" -> "练习失败：" + kc + paren(text(payload, "failureKind"));
      case "practice_succeeded" -> "练习通过：" + kc;
      case "resource_consumed" -> "学习资源：" + kc + paren(text(payload, "resourceKind"));
      case "debug_resolved" -> "调试修复：" + kc;
      default -> "学习事件：" + eventType;
    };
  }

  private static String text(JsonNode node, String key) {
    JsonNode value = node == null ? null : node.get(key);
    return value == null || value.isNull() ? "" : value.asText("");
  }

  private static String paren(String value) {
    return value == null || value.isBlank() ? "" : "（" + value + "）";
  }

  private static double clamp01(double value) {
    return Math.max(0.0, Math.min(1.0, value));
  }

  private static double round(double v) {
    return Math.round(v * 1_000.0) / 1_000.0;
  }
}
