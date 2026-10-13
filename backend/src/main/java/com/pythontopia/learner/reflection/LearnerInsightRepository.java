package com.pythontopia.learner.reflection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.learner.reflection.dto.LearnerInsightDto;
import java.sql.Array;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** {@code learner_insight} 读写：落库反思洞察、按用户倒序读取、取最新一条文本（供个性化注入）。 */
@Repository
public class LearnerInsightRepository {

  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public LearnerInsightRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  public void insert(
      Long userId,
      String text,
      String dimensionKey,
      List<String> tags,
      List<Long> evidenceEventIds,
      double confidence,
      String source) {
    jdbc.update(
        "INSERT INTO learner_insight "
            + "(user_id, text, dimension_key, tags, evidence_event_ids, confidence, source) "
            + "VALUES (?, ?, ?, ?::jsonb, ?::bigint[], ?, ?)",
        userId,
        text,
        dimensionKey,
        tagsJson(tags),
        arrayLiteral(evidenceEventIds),
        confidence,
        source);
  }

  public List<LearnerInsightDto> findByUser(Long userId, int limit) {
    return jdbc.query(
        "SELECT id, text, dimension_key, tags::text AS tags_text, evidence_event_ids, "
            + "confidence, source, created_at::text AS created_at "
            + "FROM learner_insight WHERE user_id = ? AND dimension_key = 'error_pattern' "
            + "ORDER BY created_at DESC, id DESC LIMIT ?",
        (rs, rowNum) ->
            new LearnerInsightDto(
                rs.getLong("id"),
                rs.getString("text"),
                rs.getString("dimension_key"),
                parseTags(rs.getString("tags_text")),
                readLongArray(rs.getArray("evidence_event_ids")),
                rs.getDouble("confidence"),
                rs.getString("source"),
                rs.getString("created_at")),
        userId,
        limit);
  }

  public List<LearnerInsightDto> findByUserBetween(
      Long userId, Instant since, Instant until, int limit) {
    return jdbc.query(
        "SELECT id, text, dimension_key, tags::text AS tags_text, evidence_event_ids, "
            + "confidence, source, created_at::text AS created_at "
            + "FROM learner_insight WHERE user_id = ? AND dimension_key = 'error_pattern' "
            + "AND created_at >= ? AND created_at < ? "
            + "ORDER BY created_at DESC, id DESC LIMIT ?",
        (rs, rowNum) ->
            new LearnerInsightDto(
                rs.getLong("id"),
                rs.getString("text"),
                rs.getString("dimension_key"),
                parseTags(rs.getString("tags_text")),
                readLongArray(rs.getArray("evidence_event_ids")),
                rs.getDouble("confidence"),
                rs.getString("source"),
                rs.getString("created_at")),
        userId,
        Timestamp.from(since),
        Timestamp.from(until),
        limit);
  }

  /** 取最新一条洞察文本（供资源/答疑个性化 directive 注入）；无则空。 */
  public Optional<String> findLatestText(Long userId) {
    return jdbc
        .query(
            "SELECT text FROM learner_insight WHERE user_id = ? AND dimension_key = 'error_pattern' "
                + "ORDER BY created_at DESC, id DESC LIMIT 1",
            (rs, rowNum) -> rs.getString("text"),
            userId)
        .stream()
        .findFirst();
  }

  private String tagsJson(List<String> tags) {
    try {
      return objectMapper.writeValueAsString(tags == null ? List.of() : tags);
    } catch (JsonProcessingException e) {
      return "[]";
    }
  }

  /** 构造 Postgres bigint[] 字面量，如 {@code {10,11}}；空 → {@code {}}。 */
  private static String arrayLiteral(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return "{}";
    }
    StringBuilder sb = new StringBuilder("{");
    for (int i = 0; i < ids.size(); i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(ids.get(i));
    }
    return sb.append('}').toString();
  }

  private List<String> parseTags(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      String[] arr = objectMapper.readValue(json, String[].class);
      return List.of(arr);
    } catch (JsonProcessingException e) {
      return List.of();
    }
  }

  private static List<Long> readLongArray(Array array) {
    List<Long> result = new ArrayList<>();
    if (array == null) {
      return result;
    }
    try {
      Object raw = array.getArray();
      if (raw instanceof Object[] values) {
        for (Object value : values) {
          if (value instanceof Number number) {
            result.add(number.longValue());
          }
        }
      }
    } catch (java.sql.SQLException e) {
      return result;
    }
    return result;
  }
}
