package com.pythontopia.classroom.dashboard.repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * R-15 主线教师看板的 JDBC 聚合仓储：从 §3.2 主线表（{@code chapter_star} / {@code chapter_test} /
 * {@code star_award_event} / {@code mainline_stage} / {@code mainline_stage_progress}）按班级聚合原始行，
 * 由 {@link com.pythontopia.classroom.dashboard.service.MainlineDashboardService} 在内存中完成矩阵 / 箱线 /
 * TopN 计算并加 5 分钟 TTL 缓存（§11.4：避免 N+1，50 人班 < 300ms）。
 *
 * <p>每个端点至多 2 条聚合查询，统一以 {@code classroom_members(status='ACTIVE')} 收敛班级范围。
 *
 * <p><b>依赖说明（R-15 Wave-2）</b>：§3.2 的 V156/V157 迁移由 A1 并行建表、尚未灌入任何库；本仓储按 §3.2
 * 文档的列名书写 SQL，单测对本仓储整体 mock，真库联调延后到主代理 Wave-3。
 */
@Repository
public class MainlineDashboardRepository {

  private final JdbcTemplate jdbcTemplate;

  public MainlineDashboardRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /** 班级 ACTIVE 成员数（多端点共用的「班级规模」基数）。 */
  public int countActiveMembers(Long classroomId) {
    Integer count =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM classroom_members
            WHERE classroom_id = ? AND status = 'ACTIVE'
            """,
            Integer.class,
            classroomId);
    return count == null ? 0 : count;
  }

  /**
   * 全体 ACTIVE 成员的累计星数（跨 7 章求和）+ 已得星章节数，按总星降序。 {@code chapter_star} 缺行的成员
   * 经 LEFT JOIN 仍出现（total_stars=0），用于星级 TopN 与进度矩阵的星数列。
   */
  public List<StudentStarTotalRow> findStudentStarTotals(Long classroomId) {
    return jdbcTemplate.query(
        """
        SELECT cm.student_id AS student_id,
               u.display_name AS display_name,
               COALESCE(SUM(cs.stars), 0) AS total_stars,
               COUNT(cs.id) FILTER (WHERE cs.stars > 0) AS starred_chapters
        FROM classroom_members cm
        JOIN users u ON u.id = cm.student_id
        LEFT JOIN chapter_star cs ON cs.user_id = cm.student_id
        WHERE cm.classroom_id = ? AND cm.status = 'ACTIVE'
        GROUP BY cm.student_id, u.display_name
        ORDER BY total_stars DESC, starred_chapters DESC, cm.student_id
        """,
        (rs, rowNum) ->
            new StudentStarTotalRow(
                rs.getLong("student_id"),
                rs.getString("display_name"),
                rs.getInt("total_stars"),
                rs.getInt("starred_chapters")),
        classroomId);
  }

  /**
   * 每章每星级（仅 1/2/3★）的去重人数桶。0★ 由 service 用「班级规模 − (1★+2★+3★)」反推（同时覆盖显式 0★ 行
   * 与无 {@code chapter_star} 行两种情形）。
   */
  public List<ChapterStarBucketRow> findChapterStarBuckets(Long classroomId) {
    return jdbcTemplate.query(
        """
        SELECT cs.chapter AS chapter,
               cs.stars AS stars,
               COUNT(DISTINCT cs.user_id) AS cnt
        FROM chapter_star cs
        JOIN classroom_members cm ON cm.student_id = cs.user_id AND cm.status = 'ACTIVE'
        WHERE cm.classroom_id = ? AND cs.stars > 0
        GROUP BY cs.chapter, cs.stars
        """,
        (rs, rowNum) ->
            new ChapterStarBucketRow(rs.getInt("chapter"), rs.getInt("stars"), rs.getInt("cnt")),
        classroomId);
  }

  /**
   * 每（章, 参试学生）的章测最高分 + 提交次数（仅 {@code status='SUBMITTED'} 且 {@code score} 非空）。 一条
   * 查询取齐，service 据此算参试人数 / 提交次数 / 通过率 / 分数箱线（最高分口径）。
   */
  public List<ChapterTestScoreRow> findChapterTestScores(Long classroomId) {
    return jdbcTemplate.query(
        """
        SELECT ct.chapter AS chapter,
               ct.user_id AS user_id,
               MAX(ct.score) AS best_score,
               COUNT(*) AS attempts
        FROM chapter_test ct
        JOIN classroom_members cm ON cm.student_id = ct.user_id AND cm.status = 'ACTIVE'
        WHERE cm.classroom_id = ?
          AND ct.status = 'SUBMITTED'
          AND ct.score IS NOT NULL
        GROUP BY ct.chapter, ct.user_id
        """,
        (rs, rowNum) ->
            new ChapterTestScoreRow(
                rs.getInt("chapter"),
                rs.getLong("user_id"),
                rs.getInt("best_score"),
                rs.getInt("attempts")),
        classroomId);
  }

  /**
   * 近 {@code days} 天的无尽补星事件原始行（{@code star_award_event.source='ENDLESS_RECOVERY'}），按 UTC 自然日 +
   * 章 + 学生展开，service 据此做按天 / 按章聚合与去重人数统计。每条事件即一次达标补星轮次（§7）。
   */
  public List<EndlessRecoveryRow> findEndlessRecoveryEvents(Long classroomId, int days) {
    OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minusDays(days - 1L).toLocalDate()
        .atStartOfDay().atOffset(ZoneOffset.UTC);
    return jdbcTemplate.query(
        """
        SELECT (sae.created_at AT TIME ZONE 'UTC')::date AS day,
               sae.chapter AS chapter,
               sae.user_id AS user_id
        FROM star_award_event sae
        JOIN classroom_members cm ON cm.student_id = sae.user_id AND cm.status = 'ACTIVE'
        WHERE cm.classroom_id = ?
          AND sae.source = 'ENDLESS_RECOVERY'
          AND sae.created_at >= ?
        ORDER BY day
        """,
        (rs, rowNum) ->
            new EndlessRecoveryRow(
                rs.getObject("day", LocalDate.class),
                rs.getInt("chapter"),
                rs.getLong("user_id")),
        classroomId,
        java.sql.Timestamp.from(since.toInstant()));
  }

  /** 各章 stage 总数（来自全局静态 {@code mainline_stage} seed，不按班级过滤）。 */
  public List<StageCountRow> findStageCountsPerChapter() {
    return jdbcTemplate.query(
        """
        SELECT chapter AS chapter, COUNT(*) AS stage_count
        FROM mainline_stage
        GROUP BY chapter
        ORDER BY chapter
        """,
        (rs, rowNum) -> new StageCountRow(rs.getInt("chapter"), rs.getInt("stage_count")));
  }

  /**
   * 每（成员, 章）已完成 stage 数（{@code completed_at IS NOT NULL}）。LEFT JOIN 保证零进度成员也出现（chapter
   * 为 NULL、completed=0），service 据 {@link #findStageCountsPerChapter()} 补齐缺章为 0。
   */
  public List<MemberChapterProgressRow> findMemberChapterProgress(Long classroomId) {
    return jdbcTemplate.query(
        """
        SELECT cm.student_id AS student_id,
               u.display_name AS display_name,
               ms.chapter AS chapter,
               COUNT(msp.id) FILTER (WHERE msp.completed_at IS NOT NULL) AS completed
        FROM classroom_members cm
        JOIN users u ON u.id = cm.student_id
        LEFT JOIN mainline_stage_progress msp ON msp.user_id = cm.student_id
        LEFT JOIN mainline_stage ms ON ms.id = msp.stage_id
        WHERE cm.classroom_id = ? AND cm.status = 'ACTIVE'
        GROUP BY cm.student_id, u.display_name, ms.chapter
        ORDER BY cm.student_id, ms.chapter
        """,
        (rs, rowNum) -> {
          Integer chapter = (Integer) rs.getObject("chapter");
          return new MemberChapterProgressRow(
              rs.getLong("student_id"),
              rs.getString("display_name"),
              chapter,
              rs.getInt("completed"));
        },
        classroomId);
  }

  // ── 仓储 → service 的中间行类型（按 §3.2 列名映射；service 负责矩阵/箱线/TopN 聚合） ──

  /** 单成员累计星数行。 */
  public record StudentStarTotalRow(
      long studentId, String displayName, int totalStars, int starredChapters) {}

  /** 单（章, 星级）去重人数桶（仅 1/2/3★）。 */
  public record ChapterStarBucketRow(int chapter, int stars, int count) {}

  /** 单（章, 参试学生）章测最高分 + 提交次数。 */
  public record ChapterTestScoreRow(int chapter, long userId, int bestScore, int attempts) {}

  /** 单条无尽补星事件（自然日 + 章 + 学生）。 */
  public record EndlessRecoveryRow(LocalDate day, int chapter, long userId) {}

  /** 单章 stage 总数。 */
  public record StageCountRow(int chapter, int stageCount) {}

  /** 单（成员, 章）已完成 stage 数；{@code chapter} 为 {@code null} 表示该成员暂无任何 stage 进度。 */
  public record MemberChapterProgressRow(
      long studentId, String displayName, Integer chapter, int completed) {}
}
