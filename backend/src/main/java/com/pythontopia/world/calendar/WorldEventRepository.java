package com.pythontopia.world.calendar;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.error.BusinessException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * world_event 事件日志的读写。事件由每日 {@code WorldAdvanceService} 推进产生（生日、班级公告、
 * 天气变化），供游戏内日历 UI 消费。
 */
@Repository
public class WorldEventRepository {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public WorldEventRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public List<WorldEvent> findUnprocessed(Long userId) {
    return jdbcTemplate.query(
        """
        SELECT id, user_id, date_iso, event_type, payload, is_processed, created_at
        FROM world_event
        WHERE user_id = ? AND is_processed = false
        ORDER BY date_iso DESC, id DESC
        """,
        this::map,
        userId);
  }

  public Optional<WorldEvent> findOwned(Long userId, Long eventId) {
    return jdbcTemplate
        .query(
            """
            SELECT id, user_id, date_iso, event_type, payload, is_processed, created_at
            FROM world_event
            WHERE id = ? AND user_id = ?
            """,
            this::map,
            eventId,
            userId)
        .stream()
        .findFirst();
  }

  public WorldEvent insert(
      Long userId, LocalDate dateIso, String eventType, Map<String, Object> payload) {
    Long id =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO world_event (user_id, date_iso, event_type, payload)
            VALUES (?, ?, ?, ?::jsonb)
            RETURNING id
            """,
            Long.class,
            userId,
            dateIso,
            eventType,
            writeJson(payload));
    return findOwned(userId, id).orElseThrow();
  }

  public WorldEvent markProcessed(Long userId, Long eventId) {
    int affected =
        jdbcTemplate.update(
            """
            UPDATE world_event
            SET is_processed = true
            WHERE id = ? AND user_id = ?
            """,
            eventId,
            userId);
    if (affected == 0) {
      throw new BusinessException(
          HttpStatus.NOT_FOUND, "WORLD_EVENT_NOT_FOUND", "World event " + eventId + " not found");
    }
    return findOwned(userId, eventId).orElseThrow();
  }

  private WorldEvent map(ResultSet rs, int rowNum) throws SQLException {
    return new WorldEvent(
        rs.getLong("id"),
        rs.getLong("user_id"),
        rs.getObject("date_iso", LocalDate.class),
        rs.getString("event_type"),
        readJson(rs.getString("payload")),
        rs.getBoolean("is_processed"),
        offset(rs, "created_at"));
  }

  private JsonNode readJson(String raw) {
    try {
      return raw == null ? objectMapper.createObjectNode() : objectMapper.readTree(raw);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to parse world_event.payload JSON", exception);
    }
  }

  private String writeJson(Map<String, Object> value) {
    try {
      return objectMapper.writeValueAsString(value == null ? Map.of() : value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to serialise world_event.payload", exception);
    }
  }

  private OffsetDateTime offset(ResultSet rs, String column) throws SQLException {
    java.sql.Timestamp ts = rs.getTimestamp(column);
    return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
  }
}
