package com.pythontopia.world.boss;

import com.pythontopia.world.boss.dto.ExpeditionLeaderboardDto;
import com.pythontopia.world.boss.dto.ExpeditionLeaderboardEntry;
import com.pythontopia.world.boss.dto.ExpeditionScoreSubmitDto;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 无尽远征成绩记录 + 班级排行榜（真实数据，按最高单局得分）。
 *
 * <p>每局结束写一行 {@code expedition_score}；排行榜把当前学生所在班级的全部活跃同学按各自
 * {@code MAX(score)} 降序排名。学生未入班 → 空榜 + 自身最高分，前端提示加入班级。
 */
@Service
public class ExpeditionScoreService {

  private static final int DEFAULT_LIMIT = 20;

  private final JdbcTemplate jdbcTemplate;

  public ExpeditionScoreService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /** 记录一局成绩。 */
  public void record(Long userId, ExpeditionScoreSubmitDto dto) {
    if (userId == null || dto == null) {
      return;
    }
    jdbcTemplate.update(
        "INSERT INTO expedition_score"
            + " (user_id, score, depth, best_momentum, cleared, is_daily)"
            + " VALUES (?, ?, ?, ?, ?, ?)",
        userId,
        Math.max(0, dto.score()),
        Math.max(0, dto.depth()),
        Math.max(0, dto.bestMomentum()),
        dto.cleared(),
        dto.daily());
  }

  /** 当前学生所在班级的最高单局得分排行榜（含自身名次）。 */
  public ExpeditionLeaderboardDto leaderboard(Long userId) {
    return leaderboard(userId, DEFAULT_LIMIT);
  }

  ExpeditionLeaderboardDto leaderboard(Long userId, int limit) {
    if (userId == null) {
      return new ExpeditionLeaderboardDto(List.of(), null, 0, false);
    }
    boolean inClass =
        Boolean.TRUE.equals(
            jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM classroom_members"
                    + " WHERE student_id = ? AND status = 'ACTIVE')",
                Boolean.class,
                userId));

    // 同班（含自己）所有打过远征的同学，各取最高单局得分降序。
    List<Ranked> ranked =
        jdbcTemplate.query(
            "SELECT u.id AS user_id, u.display_name AS display_name,"
                + " MAX(es.score) AS best_score, MAX(es.depth) AS best_depth"
                + " FROM expedition_score es"
                + " JOIN users u ON u.id = es.user_id"
                + " WHERE es.user_id IN ("
                + "   SELECT cm2.student_id FROM classroom_members cm2"
                + "   WHERE cm2.status = 'ACTIVE' AND cm2.classroom_id IN ("
                + "     SELECT cm.classroom_id FROM classroom_members cm"
                + "     WHERE cm.student_id = ? AND cm.status = 'ACTIVE'))"
                + " GROUP BY u.id, u.display_name"
                + " ORDER BY best_score DESC, best_depth DESC, u.id ASC",
            (rs, i) ->
                new Ranked(
                    rs.getLong("user_id"),
                    rs.getString("display_name"),
                    rs.getInt("best_score"),
                    rs.getInt("best_depth")),
            userId);

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
}
