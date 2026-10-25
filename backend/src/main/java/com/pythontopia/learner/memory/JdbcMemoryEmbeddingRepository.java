package com.pythontopia.learner.memory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Collections;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * {@link MemoryEmbeddingRepository} 的 JDBC 实现。pgvector 的 {@code vector} 类型无 JDBC 原生绑定，
 * 故以文本字面量 {@code [v1,v2,...]} 经 {@code ?::halfvec} 写入、{@code embedding::text} 读出后解析；
 * 余弦距离用 pgvector 的 {@code <=>}，相似度 = {@code 1 - 距离}。
 */
@Repository
public class JdbcMemoryEmbeddingRepository implements MemoryEmbeddingRepository {

  private final JdbcTemplate jdbc;

  public JdbcMemoryEmbeddingRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void upsert(long eventId, float[] embedding, String model) {
    jdbc.update(
        "INSERT INTO memory_embedding (event_id, embedding, model) VALUES (?, ?::halfvec, ?) "
            + "ON CONFLICT (event_id) DO UPDATE SET embedding = EXCLUDED.embedding, "
            + "model = EXCLUDED.model, created_at = now()",
        eventId,
        toVectorLiteral(embedding),
        model);
  }

  @Override
  public Optional<float[]> find(long eventId) {
    List<String> rows =
        jdbc.query(
            "SELECT embedding::text FROM memory_embedding WHERE event_id = ?",
            (rs, rowNum) -> rs.getString(1),
            eventId);
    if (rows.isEmpty() || rows.get(0) == null) {
      return Optional.empty();
    }
    return Optional.of(parseVectorLiteral(rows.get(0)));
  }

  @Override
  public Map<Long, float[]> findAll(List<Long> eventIds) {
    if (eventIds.isEmpty()) {
      return Map.of();
    }
    // shortcut: the caller caps this lookup at 30 events; chunk if reused for larger batches.
    String placeholders = String.join(",", Collections.nCopies(eventIds.size(), "?"));
    return jdbc.query(
        "SELECT event_id, embedding::text FROM memory_embedding WHERE event_id IN ("
            + placeholders
            + ")",
        rs -> {
          Map<Long, float[]> vectors = new HashMap<>();
          while (rs.next()) {
            vectors.put(rs.getLong("event_id"), parseVectorLiteral(rs.getString("embedding")));
          }
          return vectors;
        },
        eventIds.toArray());
  }

  @Override
  public List<ScoredEvent> searchByCosine(long userId, float[] query, int limit) {
    if (query == null || query.length == 0 || limit <= 0) {
      return List.of();
    }
    String literal = toVectorLiteral(query);
    return jdbc.query(
        "SELECT me.event_id, 1 - (me.embedding <=> ?::halfvec) AS similarity "
            + "FROM memory_embedding me "
            + "JOIN learning_event_log le ON le.id = me.event_id "
            + "WHERE le.user_id = ? "
            + "ORDER BY me.embedding <=> ?::halfvec "
            + "LIMIT ?",
        (rs, rowNum) -> new ScoredEvent(rs.getLong("event_id"), rs.getDouble("similarity")),
        literal,
        userId,
        literal,
        limit);
  }

  /** float[] → pgvector 字面量 {@code [v1,v2,...]}。 */
  static String toVectorLiteral(float[] vector) {
    StringBuilder builder = new StringBuilder(vector.length * 8 + 2);
    builder.append('[');
    for (int i = 0; i < vector.length; i++) {
      if (i > 0) {
        builder.append(',');
      }
      builder.append(vector[i]);
    }
    builder.append(']');
    return builder.toString();
  }

  /** pgvector 字面量 {@code [v1,v2,...]} → float[]。 */
  static float[] parseVectorLiteral(String literal) {
    String trimmed = literal.trim();
    if (trimmed.startsWith("[")) {
      trimmed = trimmed.substring(1);
    }
    if (trimmed.endsWith("]")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    if (trimmed.isBlank()) {
      return new float[0];
    }
    String[] parts = trimmed.split(",");
    float[] result = new float[parts.length];
    for (int i = 0; i < parts.length; i++) {
      result[i] = Float.parseFloat(parts[i].trim());
    }
    return result;
  }
}
