package com.pythontopia.agent.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AgentRuntimeEventRepository {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public AgentRuntimeEventRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public record EventRow(
      Long id,
      Long userId,
      String eventType,
      JsonNode payload,
      String source,
      int importance,
      OffsetDateTime createdAt) {}

  public EventRow insert(
      Long userId, String eventType, JsonNode payload, String source, int importance) {
    return jdbcTemplate.queryForObject(
        """
        INSERT INTO learning_event_log (user_id, event_type, payload, source, importance)
        VALUES (?, ?, ?::jsonb, ?, ?)
        RETURNING id, user_id, event_type, payload::text AS payload_text, source, importance, created_at
        """,
        (rs, rowNum) -> mapEvent(rs),
        userId,
        eventType,
        writeJson(payload),
        source,
        importance);
  }

  private EventRow mapEvent(ResultSet rs) throws SQLException {
    return new EventRow(
        rs.getLong("id"),
        rs.getLong("user_id"),
        rs.getString("event_type"),
        readJson(rs.getString("payload_text")),
        rs.getString("source"),
        rs.getInt("importance"),
        offset(rs, "created_at"));
  }

  private JsonNode readJson(String raw) {
    if (raw == null || raw.isBlank()) {
      return objectMapper.createObjectNode();
    }
    try {
      return objectMapper.readTree(raw);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Invalid agent runtime event JSON payload", ex);
    }
  }

  private String writeJson(JsonNode node) {
    if (node == null || node.isNull()) {
      return "{}";
    }
    return node.toString();
  }

  private OffsetDateTime offset(ResultSet rs, String column) throws SQLException {
    java.sql.Timestamp ts = rs.getTimestamp(column);
    return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
  }
}
