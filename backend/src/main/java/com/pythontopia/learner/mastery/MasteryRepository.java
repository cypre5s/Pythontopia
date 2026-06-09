package com.pythontopia.learner.mastery;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MasteryRepository {

  private final JdbcTemplate jdbcTemplate;

  public MasteryRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  Optional<MasterySnapshot> findSnapshot(Long userId, String kcCode) {
    return jdbcTemplate
        .query(
            """
            SELECT id, user_id, kc_code, mastery_score, confidence, last_event_at, updated_at
            FROM kc_mastery
            WHERE user_id = ? AND kc_code = ?
            """,
            this::mapSnapshot,
            userId,
            kcCode)
        .stream()
        .findFirst();
  }

  public List<MasterySnapshot> findAll(Long userId) {
    return jdbcTemplate.query(
        """
        SELECT id, user_id, kc_code, mastery_score, confidence, last_event_at, updated_at
        FROM kc_mastery
        WHERE user_id = ?
        ORDER BY kc_code
        """,
        this::mapSnapshot,
        userId);
  }

  Map<String, MasterySnapshot> findAllAsMap(Long userId) {
    return findAll(userId).stream()
        .collect(java.util.stream.Collectors.toMap(MasterySnapshot::kcCode, s -> s));
  }

  void upsertSnapshot(
      Long userId,
      String kcCode,
      BigDecimal masteryScore,
      BigDecimal confidence,
      OffsetDateTime lastEventAt) {
    jdbcTemplate.update(
        """
        INSERT INTO kc_mastery (user_id, kc_code, mastery_score, confidence, last_event_at, updated_at)
        VALUES (?, ?, ?, ?, ?, now())
        ON CONFLICT (user_id, kc_code) DO UPDATE SET
          mastery_score = EXCLUDED.mastery_score,
          confidence = EXCLUDED.confidence,
          last_event_at = EXCLUDED.last_event_at,
          updated_at = now()
        """,
        userId,
        kcCode,
        masteryScore,
        confidence,
        lastEventAt);
  }

  void insertEvent(
      Long userId, String kcCode, MasterySource source, BigDecimal delta, String reason) {
    jdbcTemplate.update(
        """
        INSERT INTO kc_mastery_event (user_id, kc_code, source, delta, reason)
        VALUES (?, ?, ?, ?, ?)
        """,
        userId,
        kcCode,
        source.name(),
        delta,
        reason);
  }

  public List<MasteryEventRecord> recentEvents(Long userId, String kcCode, int limit) {
    return jdbcTemplate.query(
        """
        SELECT id, user_id, kc_code, source, delta, reason, created_at
        FROM kc_mastery_event
        WHERE user_id = ? AND kc_code = ?
        ORDER BY created_at DESC
        LIMIT ?
        """,
        (rs, rowNum) ->
            new MasteryEventRecord(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getString("kc_code"),
                MasterySource.valueOf(rs.getString("source")),
                rs.getBigDecimal("delta"),
                rs.getString("reason"),
                offset(rs, "created_at")),
        userId,
        kcCode,
        limit);
  }

  private MasterySnapshot mapSnapshot(ResultSet rs, int rowNum) throws SQLException {
    return new MasterySnapshot(
        rs.getLong("id"),
        rs.getLong("user_id"),
        rs.getString("kc_code"),
        rs.getBigDecimal("mastery_score"),
        rs.getBigDecimal("confidence"),
        offset(rs, "last_event_at"),
        offset(rs, "updated_at"));
  }

  private OffsetDateTime offset(ResultSet rs, String column) throws SQLException {
    java.sql.Timestamp ts = rs.getTimestamp(column);
    return ts == null ? null : ts.toInstant().atOffset(java.time.ZoneOffset.UTC);
  }
}
