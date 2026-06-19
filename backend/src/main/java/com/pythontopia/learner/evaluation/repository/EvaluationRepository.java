package com.pythontopia.learner.evaluation.repository;

import com.pythontopia.learner.evaluation.dto.EvaluationActedStatus;
import com.pythontopia.learner.evaluation.dto.EvaluationDto;
import com.pythontopia.learner.evaluation.dto.EvaluationMetricDto;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class EvaluationRepository {

  private final JdbcTemplate jdbcTemplate;

  public EvaluationRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Long insert(
      Long userId,
      Long packId,
      OffsetDateTime periodStart,
      OffsetDateTime periodEnd,
      String narrative,
      String diff,
      Long draftPathId,
      Long activePathId) {
    GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(
        connection -> {
          PreparedStatement ps =
              connection.prepareStatement(
                  """
                  INSERT INTO learner_evaluation (
                    user_id, pack_id, period_start, period_end, narrative_summary,
                    path_diff_summary, draft_path_id, active_path_id, acted_status
                  ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')
                  """,
                  new String[] {"id"});
          ps.setLong(1, userId);
          ps.setLong(2, packId);
          ps.setObject(3, periodStart);
          ps.setObject(4, periodEnd);
          ps.setString(5, narrative);
          ps.setString(6, diff);
          if (draftPathId == null) {
            ps.setNull(7, java.sql.Types.BIGINT);
          } else {
            ps.setLong(7, draftPathId);
          }
          if (activePathId == null) {
            ps.setNull(8, java.sql.Types.BIGINT);
          } else {
            ps.setLong(8, activePathId);
          }
          return ps;
        },
        keyHolder);
    Number key = keyHolder.getKey();
    if (key == null) {
      throw new IllegalStateException("learner_evaluation insert returned no key");
    }
    return key.longValue();
  }

  public void insertMetric(Long evaluationId, EvaluationMetricDto metric) {
    jdbcTemplate.update(
        """
        INSERT INTO learner_evaluation_metric (
          evaluation_id, metric_key, numerator, denominator, value, display_unit, notes
        ) VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        evaluationId,
        metric.metricKey(),
        metric.numerator(),
        metric.denominator(),
        metric.value(),
        metric.displayUnit(),
        metric.notes());
  }

  public Optional<EvaluationDto> findById(Long userId, Long evaluationId) {
    Optional<RawEvaluation> base = jdbcTemplate.query(
        """
        SELECT id, user_id, pack_id, period_start, period_end, narrative_summary,
               path_diff_summary, draft_path_id, active_path_id, created_at, acted_status
        FROM learner_evaluation
        WHERE id = ? AND user_id = ?
        """,
        (rs, rowNum) -> mapRaw(rs),
        evaluationId,
        userId).stream().findFirst();
    if (base.isEmpty()) {
      return Optional.empty();
    }
    List<EvaluationMetricDto> metrics = findMetrics(evaluationId);
    RawEvaluation raw = base.get();
    return Optional.of(new EvaluationDto(
        raw.id(), raw.userId(), raw.packId(), raw.periodStart(), raw.periodEnd(),
        raw.narrative(), raw.diff(), raw.draftPathId(), raw.activePathId(),
        raw.createdAt(), raw.status(), metrics, null));
  }

  public List<EvaluationDto> listForUser(Long userId, int limit) {
    return jdbcTemplate.query(
        """
        SELECT id, user_id, pack_id, period_start, period_end, narrative_summary,
               path_diff_summary, draft_path_id, active_path_id, created_at, acted_status
        FROM learner_evaluation
        WHERE user_id = ?
        ORDER BY created_at DESC
        LIMIT ?
        """,
        (rs, rowNum) -> {
          RawEvaluation raw = mapRaw(rs);
          return new EvaluationDto(
              raw.id(), raw.userId(), raw.packId(), raw.periodStart(), raw.periodEnd(),
              raw.narrative(), raw.diff(), raw.draftPathId(), raw.activePathId(),
              raw.createdAt(), raw.status(), List.of(), null);
        },
        userId,
        Math.max(1, Math.min(50, limit)));
  }

  /**
   * 周报自动生成用：自 {@code since} 起有真实学习活动（提交 / 矿洞 / 资源 / 掌握事件）的学生
   * user_id（去重、升序）。仅纳入 STUDENT，避免给教师/未活动用户空跑评估。
   */
  public List<Long> findActiveLearnerIdsSince(OffsetDateTime since) {
    return jdbcTemplate.query(
        """
        SELECT u.id FROM users u
        WHERE u.role = 'STUDENT' AND (
          EXISTS (SELECT 1 FROM practice_submission ps WHERE ps.user_id = u.id AND ps.submitted_at > ?)
          OR EXISTS (SELECT 1 FROM bug_mine_session bm WHERE bm.user_id = u.id AND bm.opened_at > ?)
          OR EXISTS (SELECT 1 FROM learning_resource lr WHERE lr.user_id = u.id AND lr.created_at > ?)
          OR EXISTS (SELECT 1 FROM kc_mastery_event ke WHERE ke.user_id = u.id AND ke.created_at > ?)
        )
        ORDER BY u.id
        """,
        (rs, rowNum) -> rs.getLong("id"),
        since,
        since,
        since,
        since);
  }

  /** 幂等闸门：该用户在 {@code since} 之后是否已有评估（防一周内自动 + 手动重复生成）。 */
  public boolean existsEvaluationCreatedAfter(Long userId, OffsetDateTime since) {
    Boolean exists =
        jdbcTemplate.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM learner_evaluation WHERE user_id = ? AND created_at > ?)",
            Boolean.class,
            userId,
            since);
    return Boolean.TRUE.equals(exists);
  }

  public List<EvaluationMetricDto> findMetrics(Long evaluationId) {
    return jdbcTemplate.query(
        """
        SELECT metric_key, numerator, denominator, value, display_unit, notes
        FROM learner_evaluation_metric
        WHERE evaluation_id = ?
        ORDER BY metric_key
        """,
        (rs, rowNum) -> new EvaluationMetricDto(
            rs.getString("metric_key"),
            rs.getBigDecimal("numerator"),
            rs.getBigDecimal("denominator"),
            rs.getBigDecimal("value"),
            rs.getString("display_unit"),
            rs.getString("notes")),
        evaluationId);
  }

  public int updateActedStatus(Long evaluationId, EvaluationActedStatus status) {
    return jdbcTemplate.update(
        "UPDATE learner_evaluation SET acted_status = ? WHERE id = ?",
        status.name(),
        evaluationId);
  }

  public int updateDraftPathId(Long evaluationId, Long draftPathId) {
    return jdbcTemplate.update(
        "UPDATE learner_evaluation SET draft_path_id = ? WHERE id = ?",
        draftPathId,
        evaluationId);
  }

  public Optional<BigDecimal> findPreviousMasterySum(Long userId) {
    Long previousEvalId = jdbcTemplate.query(
        """
        SELECT id FROM learner_evaluation
        WHERE user_id = ?
        ORDER BY created_at DESC LIMIT 1
        """,
        (rs, rowNum) -> rs.getLong(1),
        userId).stream().findFirst().orElse(null);
    if (previousEvalId == null) {
      return Optional.empty();
    }
    BigDecimal value = jdbcTemplate.query(
        """
        SELECT value FROM learner_evaluation_metric
        WHERE evaluation_id = ? AND metric_key = 'kc_mastery_total'
        """,
        (rs, rowNum) -> rs.getBigDecimal("value"),
        previousEvalId).stream().findFirst().orElse(null);
    return Optional.ofNullable(value);
  }

  public BigDecimal sumMasteryTotal(Long userId) {
    BigDecimal sum = jdbcTemplate.queryForObject(
        "SELECT COALESCE(SUM(mastery_score), 0) FROM kc_mastery WHERE user_id = ?",
        BigDecimal.class,
        userId);
    return sum == null ? BigDecimal.ZERO : sum;
  }

  public int countSubmissionsAfter(Long userId, OffsetDateTime since) {
    Integer count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM practice_submission WHERE user_id = ? AND submitted_at > ?",
        Integer.class,
        userId,
        since);
    return count == null ? 0 : count;
  }

  public int countAcceptedSubmissionsAfter(Long userId, OffsetDateTime since) {
    Integer count = jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*) FROM practice_submission ps
        JOIN judge_result jr ON jr.submission_id = ps.id
        WHERE ps.user_id = ? AND ps.submitted_at > ? AND jr.verdict = 'AC'
        """,
        Integer.class,
        userId,
        since);
    return count == null ? 0 : count;
  }

  public int countBugMineOpenedAfter(Long userId, OffsetDateTime since) {
    Integer count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM bug_mine_session WHERE user_id = ? AND opened_at > ?",
        Integer.class,
        userId,
        since);
    return count == null ? 0 : count;
  }

  public int countBugMineResolvedAfter(Long userId, OffsetDateTime since) {
    Integer count = jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*) FROM bug_mine_session
        WHERE user_id = ? AND status = 'RESOLVED' AND opened_at > ?
        """,
        Integer.class,
        userId,
        since);
    return count == null ? 0 : count;
  }

  public int countProfileRevisionsBySourceAfter(
      Long userId, String source, OffsetDateTime since) {
    Integer count = jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*) FROM profile_revision
        WHERE user_id = ? AND source = ? AND created_at > ?
        """,
        Integer.class,
        userId,
        source,
        since);
    return count == null ? 0 : count;
  }

  public int countResourceFeedbackAfter(Long userId, OffsetDateTime since) {
    Integer count = jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*) FROM learning_resource
        WHERE user_id = ? AND feedback_status <> 'NONE' AND created_at > ?
        """,
        Integer.class,
        userId,
        since);
    return count == null ? 0 : count;
  }

  public int countDistinctResourceKcAfter(Long userId, OffsetDateTime since) {
    Integer count = jdbcTemplate.queryForObject(
        """
        SELECT COUNT(DISTINCT kc_code) FROM kc_mastery_event
        WHERE user_id = ? AND source = 'RESOURCE_READ' AND delta > 0 AND created_at > ?
        """,
        Integer.class,
        userId,
        since);
    return count == null ? 0 : count;
  }

  public List<KcDelta> findTopKcDelta(Long userId, OffsetDateTime since, int limit) {
    return jdbcTemplate.query(
        """
        SELECT km.kc_code AS kc_code,
               km.mastery_score AS mastery_score,
               COALESCE((
                 SELECT SUM(delta) FROM kc_mastery_event e
                 WHERE e.user_id = km.user_id AND e.kc_code = km.kc_code AND e.created_at > ?
               ), 0) AS delta_score
        FROM kc_mastery km
        WHERE km.user_id = ?
        ORDER BY ABS(COALESCE((
                 SELECT SUM(delta) FROM kc_mastery_event e
                 WHERE e.user_id = km.user_id AND e.kc_code = km.kc_code AND e.created_at > ?
               ), 0)) DESC,
               km.mastery_score DESC
        LIMIT ?
        """,
        (rs, rowNum) -> new KcDelta(
            rs.getString("kc_code"),
            rs.getBigDecimal("mastery_score"),
            rs.getBigDecimal("delta_score")),
        since,
        userId,
        since,
        Math.max(1, Math.min(20, limit)));
  }

  public List<RecentEvent> findRecentEvents(Long userId, OffsetDateTime since, int limit) {
    return jdbcTemplate.query(
        """
        SELECT 'PRACTICE_AC' AS kind, ps.submitted_at AS occurred_at,
               ('题目 ' || ps.problem_id) AS detail
        FROM practice_submission ps
        JOIN judge_result jr ON jr.submission_id = ps.id
        WHERE ps.user_id = ? AND ps.submitted_at > ? AND jr.verdict = 'AC'
        UNION ALL
        SELECT 'BUG_FIXED' AS kind, bm.closed_at AS occurred_at,
               ('修复 ' || bm.problem_id) AS detail
        FROM bug_mine_session bm
        WHERE bm.user_id = ? AND bm.status = 'RESOLVED' AND bm.closed_at > ?
        UNION ALL
        SELECT 'RESOURCE_READ' AS kind, lr.created_at AS occurred_at,
               ('生成 ' || lr.type || ' 资源') AS detail
        FROM learning_resource lr
        WHERE lr.user_id = ? AND lr.created_at > ?
        ORDER BY occurred_at DESC
        LIMIT ?
        """,
        (rs, rowNum) -> new RecentEvent(
            rs.getString("kind"),
            offset(rs, "occurred_at"),
            rs.getString("detail")),
        userId, since, userId, since, userId, since, Math.max(1, Math.min(20, limit)));
  }

  public record KcDelta(String kcCode, BigDecimal masteryScore, BigDecimal deltaScore) {}

  public record RecentEvent(String kind, OffsetDateTime occurredAt, String detail) {}

  private RawEvaluation mapRaw(ResultSet rs) throws SQLException {
    return new RawEvaluation(
        rs.getLong("id"),
        rs.getLong("user_id"),
        rs.getLong("pack_id"),
        offset(rs, "period_start"),
        offset(rs, "period_end"),
        rs.getString("narrative_summary"),
        rs.getString("path_diff_summary"),
        (Long) rs.getObject("draft_path_id"),
        (Long) rs.getObject("active_path_id"),
        offset(rs, "created_at"),
        EvaluationActedStatus.valueOf(rs.getString("acted_status")));
  }

  private OffsetDateTime offset(ResultSet rs, String column) throws SQLException {
    java.sql.Timestamp ts = rs.getTimestamp(column);
    return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
  }

  private record RawEvaluation(
      Long id,
      Long userId,
      Long packId,
      OffsetDateTime periodStart,
      OffsetDateTime periodEnd,
      String narrative,
      String diff,
      Long draftPathId,
      Long activePathId,
      OffsetDateTime createdAt,
      EvaluationActedStatus status) {}
}
