package com.pythontopia.workshop.repository;

import java.math.BigDecimal;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * {@code resource_feedback} 读写（P4.1）：学生对生成资源的采纳/评分落库，供 meta-reflection 据真实反馈
 * 驱动 agent 策略；并提供「资源 → 生成它的 agent_name」反查（经 created_by_run_id → resource_run）。
 */
@Repository
public class ResourceFeedbackRepository {

  private final JdbcTemplate jdbcTemplate;

  public ResourceFeedbackRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /** 取生成该资源的 agent_name（learning_resource.created_by_run_id → resource_run.agent_name）；无则 null。 */
  public String findAgentForResource(Long resourceId) {
    try {
      return jdbcTemplate.queryForObject(
          "SELECT rr.agent_name FROM learning_resource lr"
              + " JOIN resource_run rr ON rr.id = lr.created_by_run_id"
              + " WHERE lr.id = ?",
          String.class,
          resourceId);
    } catch (EmptyResultDataAccessException e) {
      return null;
    }
  }

  /** 落一条采纳/评分反馈。{@code rating} 可空（1..5），{@code adopted} 表示是否采纳。 */
  public void insert(
      Long userId, Long resourceId, String agentName, Integer rating, boolean adopted) {
    jdbcTemplate.update(
        "INSERT INTO resource_feedback (user_id, resource_id, agent_name, rating, adopted)"
            + " VALUES (?, ?, ?, ?, ?)",
        userId,
        resourceId,
        agentName,
        rating,
        adopted);
  }

  public ResourceEffectivenessStats effectivenessForResource(Long userId, Long resourceId) {
    return jdbcTemplate.queryForObject(
        """
        WITH feedback AS (
          SELECT
            COUNT(*) FILTER (WHERE adopted = TRUE) AS adopted_count,
            COUNT(*) FILTER (WHERE adopted = FALSE) AS rejected_count
          FROM resource_feedback
          WHERE user_id = ? AND resource_id = ?
        ),
        checks AS (
          SELECT
            COUNT(*) AS check_count,
            COUNT(*) FILTER (WHERE status = 'PASSED') AS passed_check_count,
            COUNT(*) FILTER (WHERE status = 'FAILED') AS failed_check_count,
            COALESCE(SUM(mastery_delta), 0) AS mastery_delta_sum
          FROM resource_learning_check
          WHERE user_id = ? AND resource_id = ?
        )
        SELECT adopted_count, rejected_count, check_count, passed_check_count,
               failed_check_count, mastery_delta_sum
        FROM feedback, checks
        """,
        (rs, rowNum) ->
            new ResourceEffectivenessStats(
                rs.getInt("adopted_count"),
                rs.getInt("rejected_count"),
                rs.getInt("check_count"),
                rs.getInt("passed_check_count"),
                rs.getInt("failed_check_count"),
                rs.getBigDecimal("mastery_delta_sum")),
        userId,
        resourceId,
        userId,
        resourceId);
  }

  public record ResourceEffectivenessStats(
      int adoptedCount,
      int rejectedCount,
      int checkCount,
      int passedCheckCount,
      int failedCheckCount,
      BigDecimal masteryDeltaSum) {}
}
