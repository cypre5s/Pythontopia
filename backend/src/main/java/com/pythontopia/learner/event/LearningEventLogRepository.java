package com.pythontopia.learner.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

  public void insert(Long userId, String eventType, Map<String, Object> payload, String source) {
    String payloadJson;
    try {
      payloadJson = objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      payloadJson = "{}";
    }
    jdbc.update(
        "INSERT INTO learning_event_log (user_id, event_type, payload, source) VALUES (?, ?, ?::jsonb, ?)",
        userId, eventType, payloadJson, source);
  }

  public List<Map<String, Object>> findByUser(Long userId, int limit) {
    return jdbc.queryForList(
        "SELECT id, event_type, payload::text, source, created_at FROM learning_event_log WHERE user_id = ? ORDER BY created_at DESC LIMIT ?",
        userId, limit);
  }
}
