package com.pythontopia.learner.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.learner.memory.MemoryRow;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LearningEventLogRepository {

  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public LearningEventLogRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  public long insert(
      Long userId, String eventType, Map<String, Object> payload, String source, int importance) {
    String payloadJson;
    try {
      payloadJson = objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      payloadJson = "{}";
    }
    Long id =
        jdbc.queryForObject(
            "INSERT INTO learning_event_log (user_id, event_type, payload, source, importance) "
                + "VALUES (?, ?, ?::jsonb, ?, ?) RETURNING id",
            Long.class,
            userId,
            eventType,
            payloadJson,
            source,
            importance);
    return id == null ? 0L : id;
  }

  public List<Map<String, Object>> findByUser(Long userId, int limit) {
    return jdbc.queryForList(
        "SELECT id, event_type, payload::text, source, created_at FROM learning_event_log WHERE user_id = ? ORDER BY created_at DESC LIMIT ?",
        userId, limit);
  }

  /**
   * 列出 {@code since} 以来产生过学习事件的 distinct 学生 userId（按最近活跃时间降序，至多 {@code limit} 个）。
   *
   * <p>供 NPC 寒暄「每日预生成」任务挑选「有记忆的活跃学生」，避免给历史全量学生跑 LLM。
   */
  public List<Long> findActiveUserIds(Instant since, int limit) {
    return jdbc.queryForList(
        "SELECT user_id FROM learning_event_log WHERE created_at >= ? "
            + "GROUP BY user_id ORDER BY MAX(created_at) DESC LIMIT ?",
        Long.class,
        Timestamp.from(since),
        limit);
  }

  /**
   * 取该用户最近 {@code windowSize} 条事件并结构化为 {@link MemoryRow}（含解析后的 payload 与抽取的 KC
   * code），供记忆流检索打分。payload JSON 非法时降级为空节点 + 空 KC，单行失败不影响整体。
   */
  public List<MemoryRow> findRecentForMemory(Long userId, int windowSize) {
    return jdbc.query(
        "SELECT id, event_type, payload::text AS payload_text, importance, created_at "
            + "FROM learning_event_log WHERE user_id = ? ORDER BY created_at DESC LIMIT ?",
        (rs, rowNum) -> {
          long id = rs.getLong("id");
          String eventType = rs.getString("event_type");
          int importance = rs.getInt("importance");
          Timestamp ts = rs.getTimestamp("created_at");
          Instant createdAt = ts == null ? null : ts.toInstant();
          JsonNode payload = parsePayload(rs.getString("payload_text"));
          return new MemoryRow(id, eventType, extractKcCodes(payload), importance, createdAt, payload);
        },
        userId,
        windowSize);
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

  /** 从 payload 抽取相关 KC：优先 {@code kcCodes} 数组，回退 {@code kcCode} 单值。 */
  private static List<String> extractKcCodes(JsonNode payload) {
    List<String> codes = new ArrayList<>();
    if (payload == null) {
      return codes;
    }
    JsonNode kcArray = payload.get("kcCodes");
    if (kcArray != null && kcArray.isArray()) {
      for (JsonNode node : kcArray) {
        if (node != null && !node.isNull()) {
          String code = node.asText("");
          if (!code.isBlank()) {
            codes.add(code);
          }
        }
      }
    }
    JsonNode single = payload.get("kcCode");
    if (single != null && single.isTextual()) {
      String code = single.asText("");
      if (!code.isBlank() && !codes.contains(code)) {
        codes.add(code);
      }
    }
    return codes;
  }
}
