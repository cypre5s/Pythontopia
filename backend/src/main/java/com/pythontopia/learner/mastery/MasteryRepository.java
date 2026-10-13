package com.pythontopia.learner.mastery;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Repository;

@Repository
public class MasteryRepository {

  private final JdbcTemplate jdbcTemplate;

  public MasteryRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  void lockSnapshot(Long userId, String kcCode) {
    jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
      try (PreparedStatement statement = connection.prepareStatement(
          "SELECT pg_advisory_xact_lock(hashtextextended(CAST(? AS TEXT) || ':' || ?, 0))")) {
        statement.setLong(1, userId);
        statement.setString(2, kcCode);
        statement.execute();
      }
      return null;
    });
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

  /** KC codes backed by verified practice evidence; bootstrap/default snapshots do not count. */
  public Set<String> findAssessedKcCodes(Long userId) {
    return Set.copyOf(jdbcTemplate.queryForList(
        "SELECT DISTINCT kc_code FROM kc_mastery_event WHERE user_id = ?"
            + " AND source IN ('PRACTICE_PASS', 'PRACTICE_FAIL', 'MICRO_CHECK_PASS',"
            + " 'MICRO_CHECK_FAIL', 'DEBUG_FIX')",
        String.class,
        userId));
  }

  /** Latest three server-recorded practice outcomes per KC, newest first. */
  public List<PracticeOutcome> findRecentPracticeOutcomesByUser(Long userId) {
    return jdbcTemplate.query(
        "SELECT kc_code, source, created_at FROM ("
            + " SELECT kc_code, source, created_at, ROW_NUMBER() OVER (PARTITION BY kc_code"
            + " ORDER BY created_at DESC, id DESC) AS row_number"
            + " FROM kc_mastery_event WHERE user_id = ? AND source IN"
            + " ('PRACTICE_PASS', 'PRACTICE_FAIL')"
            + ") recent WHERE row_number <= 3 ORDER BY kc_code, row_number",
        (rs, rowNum) ->
            new PracticeOutcome(
                rs.getString("kc_code"),
                rs.getString("source").endsWith("PASS") || "DEBUG_FIX".equals(rs.getString("source")),
                offset(rs, "created_at")),
        userId);
  }

  public record PracticeOutcome(String kcCode, boolean passed, OffsetDateTime createdAt) {}

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
      Long userId,
      String kcCode,
      MasterySource source,
      BigDecimal delta,
      BigDecimal appliedDelta,
      String reason) {
    jdbcTemplate.update(
        """
        INSERT INTO kc_mastery_event (user_id, kc_code, source, delta, applied_delta, reason)
        VALUES (?, ?, ?, ?, ?, ?)
        """,
        userId,
        kcCode,
        source.name(),
        delta,
        appliedDelta,
        reason);
  }

  boolean insertEventIfNew(
      Long userId,
      String kcCode,
      MasterySource source,
      BigDecimal delta,
      BigDecimal appliedDelta,
      String reason,
      String idempotencyKey) {
    return !jdbcTemplate.query(
            "INSERT INTO kc_mastery_event (user_id, kc_code, source, delta, applied_delta, reason, idempotency_key)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (user_id, idempotency_key)"
                + " WHERE idempotency_key IS NOT NULL DO NOTHING RETURNING id",
            (rs, rowNum) -> rs.getLong("id"),
            userId,
            kcCode,
            source.name(),
            delta,
            appliedDelta,
            reason,
            idempotencyKey)
        .isEmpty();
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

  public Map<String, OffsetDateTime> findLastPracticedAtByUser(Long userId) {
    return jdbcTemplate.query(
            "SELECT kc_code, MAX(created_at) AS last_practiced_at FROM kc_mastery_event"
                + " WHERE user_id = ? AND source IN ('PRACTICE_PASS', 'PRACTICE_FAIL', 'DEBUG_FIX')"
                + " GROUP BY kc_code",
            (rs, rowNum) ->
                Map.entry(
                    rs.getString("kc_code"),
                    offset(rs, "last_practiced_at")),
            userId)
        .stream()
        .filter(entry -> entry.getValue() != null)
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  public List<WeeklyEvidence> findWeeklyEvidence(
      Long userId, OffsetDateTime since, OffsetDateTime until) {
    return jdbcTemplate.query(
        "SELECT kc_code, CASE WHEN COUNT(*) FILTER (WHERE applied_delta IS NULL) = 0"
            + " THEN SUM(applied_delta) END AS weekly_delta, COUNT(*) AS evidence_count,"
            + " ARRAY_AGG(DISTINCT source ORDER BY source) AS evidence_sources"
            + " FROM kc_mastery_event WHERE user_id = ? AND created_at >= ? AND created_at < ?"
            + " AND source IN ('PRACTICE_PASS', 'PRACTICE_FAIL', 'DEBUG_FIX',"
            + " 'MICRO_CHECK_PASS', 'MICRO_CHECK_FAIL')"
            + " GROUP BY kc_code ORDER BY kc_code",
        (rs, rowNum) ->
            new WeeklyEvidence(
                rs.getString("kc_code"),
                rs.getBigDecimal("weekly_delta"),
                rs.getInt("evidence_count"),
                readStringArray(rs.getArray("evidence_sources"))),
        userId,
        since,
        until);
  }

  private static List<String> readStringArray(java.sql.Array array) {
    List<String> values = new ArrayList<>();
    if (array == null) return values;
    try {
      Object raw = array.getArray();
      if (raw instanceof Object[] items) {
        for (Object item : items) {
          if (item instanceof String value) values.add(value);
        }
      }
    } catch (SQLException ignored) {
      return List.of();
    }
    return List.copyOf(values);
  }

  public record WeeklyEvidence(
      String kcCode, BigDecimal weeklyDelta, int evidenceCount, List<String> evidenceSources) {
    public WeeklyEvidence {
      evidenceSources = evidenceSources == null ? List.of() : List.copyOf(evidenceSources);
    }
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
