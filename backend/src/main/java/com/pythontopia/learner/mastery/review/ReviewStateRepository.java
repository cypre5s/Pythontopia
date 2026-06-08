package com.pythontopia.learner.mastery.review;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** kc_review_state 的读写；FSRS-lite 状态的唯一持久化入口。 */
@Repository
public class ReviewStateRepository {

  private final JdbcTemplate jdbcTemplate;

  public ReviewStateRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<ReviewState> find(Long userId, String kcCode) {
    return jdbcTemplate
        .query(
            """
            SELECT id, user_id, kc_code, stability, difficulty, reps, lapses,
                   last_review_at, due_at, created_at, updated_at
            FROM kc_review_state
            WHERE user_id = ? AND kc_code = ?
            """,
            this::map,
            userId,
            kcCode)
        .stream()
        .findFirst();
  }

  public List<ReviewState> findAll(Long userId) {
    return jdbcTemplate.query(
        """
        SELECT id, user_id, kc_code, stability, difficulty, reps, lapses,
               last_review_at, due_at, created_at, updated_at
        FROM kc_review_state
        WHERE user_id = ?
        ORDER BY due_at NULLS LAST
        """,
        this::map,
        userId);
  }

  public void upsert(
      Long userId,
      String kcCode,
      double stability,
      double difficulty,
      int reps,
      int lapses,
      OffsetDateTime lastReviewAt,
      OffsetDateTime dueAt) {
    jdbcTemplate.update(
        """
        INSERT INTO kc_review_state (
          user_id, kc_code, stability, difficulty, reps, lapses, last_review_at, due_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, now())
        ON CONFLICT (user_id, kc_code) DO UPDATE SET
          stability = EXCLUDED.stability,
          difficulty = EXCLUDED.difficulty,
          reps = EXCLUDED.reps,
          lapses = EXCLUDED.lapses,
          last_review_at = EXCLUDED.last_review_at,
          due_at = EXCLUDED.due_at,
          updated_at = now()
        """,
        userId,
        kcCode,
        stability,
        difficulty,
        reps,
        lapses,
        lastReviewAt,
        dueAt);
  }

  private ReviewState map(ResultSet rs, int rowNum) throws SQLException {
    return new ReviewState(
        rs.getLong("id"),
        rs.getLong("user_id"),
        rs.getString("kc_code"),
        rs.getBigDecimal("stability").doubleValue(),
        rs.getBigDecimal("difficulty").doubleValue(),
        rs.getInt("reps"),
        rs.getInt("lapses"),
        offset(rs, "last_review_at"),
        offset(rs, "due_at"),
        offset(rs, "created_at"),
        offset(rs, "updated_at"));
  }

  private OffsetDateTime offset(ResultSet rs, String column) throws SQLException {
    java.sql.Timestamp ts = rs.getTimestamp(column);
    return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
  }
}
