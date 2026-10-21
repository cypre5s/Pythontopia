package com.pythontopia.learner.effect;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LearningEffectTraceRepository {

  private final JdbcTemplate jdbc;

  public LearningEffectTraceRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
  }

  public LearningEffectSummaryDto summary(Long userId) {
    int resources =
        count("SELECT COUNT(*) FROM learning_resource WHERE user_id = ?", userId);
    int consumed =
        count(
            """
            SELECT COUNT(DISTINCT (payload->>'resourceId')::bigint)
            FROM learning_event_log
            WHERE user_id = ? AND event_type = 'resource_consumed'
              AND jsonb_exists(payload, 'resourceId')
            """,
            userId);
    int checkedResources =
        count(
            "SELECT COUNT(DISTINCT resource_id) FROM resource_learning_check WHERE user_id = ?",
            userId);
    int checks =
        count("SELECT COUNT(*) FROM resource_learning_check WHERE user_id = ?", userId);
    int passed =
        count(
            "SELECT COUNT(*) FROM resource_learning_check WHERE user_id = ? AND status = 'PASSED'",
            userId);
    int failed =
        count(
            "SELECT COUNT(*) FROM resource_learning_check WHERE user_id = ? AND status = 'FAILED'",
            userId);
    int remedied =
        count(
            """
            SELECT COUNT(*) FROM resource_learning_check
            WHERE user_id = ? AND status = 'FAILED' AND remediation_type IS NOT NULL
            """,
            userId);
    BigDecimal totalDelta =
        decimal(
            """
            SELECT COALESCE(SUM(delta), 0)
            FROM kc_mastery_event
            WHERE user_id = ? AND source IN ('MICRO_CHECK_PASS', 'MICRO_CHECK_FAIL')
            """,
            userId);
    int positiveResourceRead =
        count(
            """
            SELECT COUNT(*) FROM kc_mastery_event
            WHERE user_id = ? AND source = 'RESOURCE_READ' AND delta > 0
            """,
            userId);
    int checkMasteryEvents =
        count(
            """
            SELECT COUNT(*) FROM kc_mastery_event
            WHERE user_id = ? AND source IN ('MICRO_CHECK_PASS', 'MICRO_CHECK_FAIL')
            """,
            userId);
    return new LearningEffectSummaryDto(
        resources,
        consumed,
        checkedResources,
        checks,
        passed,
        failed,
        ratio(passed, checks),
        ratio(checkedResources, consumed),
        // 补救率的分母是「失败微测」而不是全部微测：remedied 只统计 status='FAILED' AND
        // remediation_type IS NOT NULL，用全部微测作分母会让「1 次失败已补救 + 99 次通过」
        // 算出 0.01，与「补救率」的自然定义（已补救 / 需补救）相差两个量级。
        // failedCheckCount = 0 时返回 0.0 表示「不适用」（无失败即无需补救），
        // 消费方必须结合 failedCheckCount 判读，见 TutorBenchEvaluator.antiCheatRemediation。
        ratio(remedied, failed),
        totalDelta,
        positiveResourceRead,
        checkMasteryEvents);
  }

  private int count(String sql, Long userId) {
    Integer value = jdbc.queryForObject(sql, Integer.class, userId);
    return value == null ? 0 : value;
  }

  private BigDecimal decimal(String sql, Long userId) {
    BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class, userId);
    return value == null ? BigDecimal.ZERO : value;
  }

  private static double ratio(int numerator, int denominator) {
    if (denominator <= 0) {
      return 0.0;
    }
    return Math.round(((double) numerator / (double) denominator) * 1000.0) / 1000.0;
  }
}
