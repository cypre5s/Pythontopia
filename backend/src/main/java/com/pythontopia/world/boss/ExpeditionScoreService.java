package com.pythontopia.world.boss;

import com.pythontopia.world.boss.dto.ExpeditionLeaderboardDto;
import com.pythontopia.world.boss.dto.ExpeditionLeaderboardEntry;
import com.pythontopia.world.boss.dto.ExpeditionScoreSubmitDto;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 无尽远征成绩记录 + 班级排行榜（真实数据，按最高单局得分）。
 *
 * <p>排行榜只收录由已判题 attempt 推导出的分数。客户端只能请求结算 runId，不能提交成绩字段。
 */
@Service
public class ExpeditionScoreService {

  private static final int DEFAULT_LIMIT = 20;
  // shortcut: current map has 9 challenge levels and 3 boss phases; revise with map topology changes.
  private static final int MAX_VERIFIED_ATTEMPTS_PER_RUN = 12;

  private final JdbcTemplate jdbcTemplate;

  public ExpeditionScoreService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /** 按服务端保存的判题结果结算一局；同一 user/run 只能结算一次。 */
  @Transactional
  public void record(Long userId, ExpeditionScoreSubmitDto dto) {
    if (userId == null || dto == null || dto.runId() == null) {
      return;
    }
    UUID runId = UUID.fromString(dto.runId());
    if (jdbcTemplate
        .query(
            "SELECT 1 FROM expedition_run WHERE user_id = ? AND run_id = ?::uuid"
                + " AND status = 'ACTIVE' FOR UPDATE",
            (rs, rowNum) -> rs.getInt(1),
            userId,
            runId)
        .isEmpty()) {
      return;
    }
    List<VerifiedAttempt> attempts =
        jdbcTemplate.query(
            "SELECT passed, score FROM expedition_verified_attempt"
                + " WHERE user_id = ? AND run_id = ?::uuid"
                + " ORDER BY created_at, id LIMIT " + MAX_VERIFIED_ATTEMPTS_PER_RUN,
            (rs, rowNum) -> new VerifiedAttempt(rs.getBoolean("passed"), rs.getInt("score")),
            userId,
            runId);
    if (!attempts.isEmpty()) {
      int score = 0;
      int depth = 0;
      int momentum = 0;
      int bestMomentum = 0;
      for (VerifiedAttempt attempt : attempts) {
        if (attempt.passed()) {
          score += attempt.score();
          depth++;
          momentum++;
          bestMomentum = Math.max(bestMomentum, momentum);
        } else {
          momentum = 0;
        }
      }
      jdbcTemplate.update(
          "INSERT INTO expedition_score"
              + " (user_id, run_id, score, depth, best_momentum, cleared, is_daily, verified)"
              + " VALUES (?, ?::uuid, ?, ?, ?, FALSE, FALSE, TRUE)"
              + " ON CONFLICT (user_id, run_id) WHERE run_id IS NOT NULL DO NOTHING",
          userId,
          runId,
          score,
          depth,
          bestMomentum);
    }
    jdbcTemplate.update(
        "UPDATE expedition_run SET status = 'SETTLED', phase = 'over', updated_at = now()"
            + " WHERE user_id = ? AND run_id = ?::uuid AND status = 'ACTIVE'",
        userId,
        runId);
  }

  /** 只记录实际判题结果；同一题可从失败更新为通过，但不能重复累计。 */
  @Transactional
  public void recordVerifiedAttempt(
      Long userId, String runId, String problemId, boolean passed, int score) {
    if (userId == null || runId == null || problemId == null) {
      return;
    }
    UUID runUuid = UUID.fromString(runId);
    // Locking the run row serializes budget checks for parallel submissions in this run.
    if (jdbcTemplate
        .query(
            "SELECT 1 FROM expedition_run WHERE user_id = ? AND run_id = ?::uuid"
                + " AND status = 'ACTIVE' FOR UPDATE",
            (rs, rowNum) -> rs.getInt(1),
            userId,
            runUuid)
        .isEmpty()) {
      return;
    }
    jdbcTemplate.update(
        "INSERT INTO expedition_verified_attempt (user_id, run_id, problem_id, passed, score)"
            + " SELECT ?, ?::uuid, ?, ?, ?"
            + " WHERE EXISTS (SELECT 1 FROM expedition_run WHERE user_id = ?"
            + " AND run_id = ?::uuid AND status = 'ACTIVE')"
            + " AND NOT EXISTS (SELECT 1 FROM expedition_score WHERE user_id = ? AND run_id = ?::uuid)"
            + " AND ((SELECT COUNT(*) FROM expedition_verified_attempt"
            + " WHERE user_id = ? AND run_id = ?::uuid) < " + MAX_VERIFIED_ATTEMPTS_PER_RUN
            + " OR EXISTS (SELECT 1 FROM expedition_verified_attempt"
            + " WHERE user_id = ? AND run_id = ?::uuid AND problem_id = ?))"
            + " ON CONFLICT (user_id, run_id, problem_id) DO UPDATE SET"
            + " passed = expedition_verified_attempt.passed OR EXCLUDED.passed,"
            + " score = GREATEST(expedition_verified_attempt.score, EXCLUDED.score)",
        userId,
        runUuid,
        problemId,
        passed,
        passed ? Math.max(0, score) : 0,
        userId,
        runUuid,
        userId,
        runUuid,
        userId,
        runUuid,
        userId,
        runUuid,
        problemId);
  }

  /** 判定一个活动远征 run 在指定章节的真实答题汇总。 */
  public VerifiedAttemptSummary summarizeVerifiedAttempts(
      Long userId, String runId, int chapter) {
    if (userId == null || runId == null) {
      return new VerifiedAttemptSummary(0, 0);
    }
    UUID runUuid;
    try {
      runUuid = UUID.fromString(runId);
    } catch (IllegalArgumentException exception) {
      return new VerifiedAttemptSummary(0, 0);
    }
    return jdbcTemplate
        .query(
            "SELECT COUNT(*) AS total, COUNT(*) FILTER (WHERE a.passed) AS correct"
                + " FROM expedition_verified_attempt a"
                + " JOIN expedition_run r ON r.user_id = a.user_id AND r.run_id = a.run_id"
                + " AND r.status = 'ACTIVE'"
                + " WHERE a.user_id = ? AND a.run_id = ?::uuid"
                + " AND EXISTS (SELECT 1 FROM pregenerated_problem p"
                + " WHERE p.user_id = a.user_id AND p.problem_id = a.problem_id"
                + " AND p.expedition_run_id = a.run_id AND p.chapter = ?)",
            (rs, rowNum) ->
                new VerifiedAttemptSummary(rs.getInt("total"), rs.getInt("correct")),
            userId,
            runUuid,
            chapter)
        .stream()
        .findFirst()
        .orElse(new VerifiedAttemptSummary(0, 0));
  }

  /** 当前学生所在班级的最高单局得分排行榜（含自身名次）。 */
  public ExpeditionLeaderboardDto leaderboard(Long userId) {
    return leaderboard(userId, DEFAULT_LIMIT);
  }

  ExpeditionLeaderboardDto leaderboard(Long userId, int limit) {
    if (userId == null) {
      return new ExpeditionLeaderboardDto(List.of(), null, 0, false);
    }
    Long classroomId =
        jdbcTemplate
            .query(
                "SELECT classroom_id FROM classroom_members"
                    + " WHERE student_id = ? AND status = 'ACTIVE'"
                    + " ORDER BY classroom_id LIMIT 1",
                (rs, rowNum) -> rs.getLong(1),
                userId)
            .stream()
            .findFirst()
            .orElse(null);
    boolean inClass = classroomId != null;
    if (!inClass) {
      return new ExpeditionLeaderboardDto(List.of(), null, 0, false);
    }

    // 一个明确班级内每人取同一局的最高分和深度，避免跨班聚合及 MAX 字段错配。
    List<Ranked> ranked =
        jdbcTemplate.query(
            "WITH best AS (SELECT DISTINCT ON (es.user_id) es.user_id, es.score, es.depth"
                + " FROM expedition_score es JOIN classroom_members cm"
                + " ON cm.student_id = es.user_id AND cm.classroom_id = ? AND cm.status = 'ACTIVE'"
                + " WHERE es.verified = TRUE"
                + " ORDER BY es.user_id, es.score DESC, es.depth DESC, es.created_at DESC, es.id DESC)"
                + " SELECT u.id AS user_id, u.display_name AS display_name,"
                + " best.score AS best_score, best.depth AS best_depth"
                + " FROM best JOIN users u ON u.id = best.user_id"
                + " ORDER BY best.score DESC, best.depth DESC, u.id ASC",
            (rs, i) ->
                new Ranked(
                    rs.getLong("user_id"),
                    rs.getString("display_name"),
                    rs.getInt("best_score"),
                    rs.getInt("best_depth")),
            classroomId);

    Integer selfRank = null;
    int selfBest = 0;
    List<ExpeditionLeaderboardEntry> entries = new ArrayList<>();
    int cap = Math.max(1, limit);
    for (int idx = 0; idx < ranked.size(); idx++) {
      Ranked r = ranked.get(idx);
      boolean self = r.userId() == userId;
      if (self) {
        selfRank = idx + 1;
        selfBest = r.bestScore();
      }
      if (idx < cap) {
        entries.add(
            new ExpeditionLeaderboardEntry(
                idx + 1, r.userId(), r.displayName(), r.bestScore(), r.bestDepth(), self));
      }
    }
    return new ExpeditionLeaderboardDto(entries, selfRank, selfBest, inClass);
  }

  private record Ranked(long userId, String displayName, int bestScore, int bestDepth) {}

  record VerifiedAttempt(boolean passed, int score) {}

  public record VerifiedAttemptSummary(int total, int correct) {}
}
