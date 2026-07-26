package com.pythontopia.dialog;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 对话上下文聚合查询。
 *
 * <p>所有 SQL 都按 user_id / classroom_id 做了 owner filter，禁止跨用户跨班级泄露；各方法返回值
 * 统一用 {@link Optional} 或空集合，调用方（{@link DialogContextService}）负责兜底，不把空数据当异常抛。
 *
 * <p>这里只保留「模板真会用到」的聚合：每加一个方法都意味着每次 NPC 对话多一条 SQL，所以新增前
 * 必须先在 {@code data/npcs/&#42;/dialog.json} 里存在对应 {@code {{stats.*}}} 占位符。R-14 收缩残留的
 * 能量 / 每日题 / 推荐题 / 同伴对比 / 作物枯萎成熟等查询已随无人消费的 key 一并删除。
 */
@Repository
class DialogContextRepository {

  private final JdbcTemplate jdbcTemplate;

  DialogContextRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  int countSubmissions(Long userId, OffsetDateTime since, OffsetDateTime until) {
    Integer count =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM practice_submission
            WHERE user_id = ? AND submitted_at >= ? AND submitted_at < ?
            """,
            Integer.class,
            userId,
            since,
            until);
    return count == null ? 0 : count;
  }

  int countFailedSubmissions(Long userId, OffsetDateTime since, OffsetDateTime until) {
    Integer count =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM practice_submission ps
            JOIN judge_result jr ON jr.submission_id = ps.id
            WHERE ps.user_id = ?
              AND ps.submitted_at >= ? AND ps.submitted_at < ?
              AND jr.verdict NOT IN ('AC')
            """,
            Integer.class,
            userId,
            since,
            until);
    return count == null ? 0 : count;
  }

  /** 查 KC 所属章节：chapter_order、标题、page_no 范围、首个示例页。 */
  Optional<KcChapterRow> findKcChapter(String kcCode) {
    if (kcCode == null || kcCode.isBlank()) {
      return Optional.empty();
    }
    return jdbcTemplate
        .query(
            """
            SELECT kc.chapter_order, ch.title,
                   (SELECT MIN(p.page_no) FROM course_page p WHERE p.id = ANY(kc.page_refs)) AS page_min,
                   (SELECT MAX(p.page_no) FROM course_page p WHERE p.id = ANY(kc.page_refs)) AS page_max,
                   (SELECT MIN(p2.page_no) FROM course_page p2
                    JOIN course_example ex ON p2.id = ANY(ex.page_refs)
                    WHERE ex.pack_id = 43 AND ex.kc_code = kc.kc_code) AS example_page
            FROM course_kc kc
            LEFT JOIN course_chapter ch ON ch.id = kc.chapter_id
            WHERE kc.pack_id = 43 AND kc.kc_code = ?
            """,
            (rs, rowNum) ->
                new KcChapterRow(
                    (Integer) rs.getObject("chapter_order"),
                    rs.getString("title"),
                    (Integer) rs.getObject("page_min"),
                    (Integer) rs.getObject("page_max"),
                    (Integer) rs.getObject("example_page")),
            kcCode)
        .stream()
        .findFirst();
  }

  /** 按本周失败提交频次倒序的 KC 排行，最多取前 N 个。 */
  List<KcFailureRow> findToughestKcs(
      Long userId, OffsetDateTime since, OffsetDateTime until, int limit) {
    return jdbcTemplate.query(
        """
        SELECT cp.kc_code, COALESCE(kc.kc_name, cp.kc_code) AS kc_name, COUNT(*) AS failed_count
        FROM practice_submission ps
        JOIN judge_result jr ON jr.submission_id = ps.id
        JOIN course_problem cp ON cp.id = ps.problem_id
        LEFT JOIN course_kc kc ON kc.kc_code = cp.kc_code AND kc.pack_id = 43
        WHERE ps.user_id = ?
          AND ps.submitted_at >= ? AND ps.submitted_at < ?
          AND jr.verdict NOT IN ('AC')
        GROUP BY cp.kc_code, kc.kc_name
        ORDER BY failed_count DESC
        LIMIT ?
        """,
        (rs, rowNum) ->
            new KcFailureRow(
                rs.getString("kc_code"), rs.getString("kc_name"), rs.getInt("failed_count")),
        userId,
        since,
        until,
        limit);
  }

  /** 全时段 verdict 分布（top mistake pattern 用）。 */
  List<VerdictCountRow> findVerdictDistribution(Long userId, int limit) {
    return jdbcTemplate.query(
        """
        SELECT jr.verdict, COUNT(*) AS cnt
        FROM practice_submission ps
        JOIN judge_result jr ON jr.submission_id = ps.id
        WHERE ps.user_id = ? AND jr.verdict NOT IN ('AC')
        GROUP BY jr.verdict
        ORDER BY cnt DESC
        LIMIT ?
        """,
        (rs, rowNum) -> new VerdictCountRow(rs.getString("verdict"), rs.getInt("cnt")),
        userId,
        limit);
  }

  /** 最近一次失败提交（题面 + verdict）。 */
  Optional<LastFailureRow> findLastFailure(Long userId) {
    return jdbcTemplate
        .query(
            """
            SELECT ps.problem_id, cp.title, jr.verdict
            FROM practice_submission ps
            JOIN judge_result jr ON jr.submission_id = ps.id
            JOIN course_problem cp ON cp.id = ps.problem_id
            WHERE ps.user_id = ? AND jr.verdict NOT IN ('AC')
            ORDER BY ps.submitted_at DESC
            LIMIT 1
            """,
            (rs, rowNum) ->
                new LastFailureRow(
                    rs.getLong("problem_id"), rs.getString("title"), rs.getString("verdict")),
            userId)
        .stream()
        .findFirst();
  }

  // R-14 收缩后作物系统退役（V126 已 DROP crop_tile_state），以下「解锁/枯萎/成熟」
  // 语义统一改读 kc_mastery：达标阈值 0.4 与主线「测·达标」一致。

  /**
   * 时间段内「新达标」的 KC 数（近似口径：当前掌握度 ≥ 0.4 且最近一次学习事件落在该区间）。
   * 仅用于 NPC 对话的周报文案，不参与任何判定/奖励，近似可接受。
   */
  int countKcReachedThresholdInRange(Long userId, OffsetDateTime since, OffsetDateTime until) {
    Integer count =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM kc_mastery
            WHERE user_id = ? AND mastery_score >= 0.4
              AND last_event_at IS NOT NULL
              AND last_event_at >= ? AND last_event_at < ?
            """,
            Integer.class,
            userId,
            since,
            until);
    return count == null ? 0 : count;
  }

  /** 已达标 KC 名（前 N 个，按章节顺序）。 */
  List<String> findUnlockedKcNames(Long userId, int limit) {
    return jdbcTemplate.query(
        """
        SELECT COALESCE(kc.kc_name, m.kc_code) AS kc_name
        FROM kc_mastery m
        LEFT JOIN course_kc kc ON kc.kc_code = m.kc_code AND kc.pack_id = 43
        WHERE m.user_id = ? AND m.mastery_score >= 0.4
        ORDER BY COALESCE(kc.chapter_order, 99), m.kc_code
        LIMIT ?
        """,
        (rs, rowNum) -> rs.getString("kc_name"),
        userId,
        limit);
  }

  /** 该用户所有 KC 中 mastery 最低的一个（lowestMasteryKcName 用）。 */
  Optional<KcMasteryRow> findLowestMasteryKc(Long userId) {
    return jdbcTemplate
        .query(
            """
            SELECT m.kc_code, COALESCE(kc.kc_name, m.kc_code) AS kc_name, m.mastery_score
            FROM kc_mastery m
            LEFT JOIN course_kc kc ON kc.kc_code = m.kc_code AND kc.pack_id = 43
            WHERE m.user_id = ?
            ORDER BY m.mastery_score ASC, m.kc_code
            LIMIT 1
            """,
            (rs, rowNum) ->
                new KcMasteryRow(
                    rs.getString("kc_code"),
                    rs.getString("kc_name"),
                    rs.getBigDecimal("mastery_score")),
            userId)
        .stream()
        .findFirst();
  }

  Optional<BigDecimal> findAverageMastery(Long userId) {
    return Optional.ofNullable(
        jdbcTemplate.queryForObject(
            "SELECT AVG(mastery_score) FROM kc_mastery WHERE user_id = ?",
            BigDecimal.class,
            userId));
  }

  /** 当前章节序：mastery ≥ 0.5 的 KC 中最大的 chapter_order。 */
  Optional<Integer> findCurrentChapterOrder(Long userId) {
    return jdbcTemplate
        .query(
            """
            SELECT kc.chapter_order
            FROM kc_mastery m
            JOIN course_kc kc ON kc.kc_code = m.kc_code AND kc.pack_id = 43
            WHERE m.user_id = ? AND m.mastery_score >= 0.5
            ORDER BY kc.chapter_order DESC
            LIMIT 1
            """,
            (rs, rowNum) -> (Integer) rs.getObject("chapter_order"),
            userId)
        .stream()
        .findFirst();
  }

  /** 推荐下一章节：当前 currentChapter 之后第一个还有未达标 KC 的章节。 */
  Optional<KcChapterRow> findRecommendChapter(Long userId, int afterOrder) {
    return jdbcTemplate
        .query(
            """
            WITH lacking AS (
              SELECT kc.chapter_id, MIN(kc.chapter_order) AS chap_order
              FROM course_kc kc
              LEFT JOIN kc_mastery m ON m.kc_code = kc.kc_code AND m.user_id = ?
              WHERE kc.pack_id = 43
                AND kc.chapter_order > ?
                AND (m.mastery_score IS NULL OR m.mastery_score < 0.5)
              GROUP BY kc.chapter_id
              ORDER BY chap_order ASC
              LIMIT 1
            )
            SELECT ch.order_index AS chapter_order, ch.title,
                   (SELECT MIN(p.page_no) FROM course_page p WHERE p.chapter_id = ch.id) AS page_min,
                   (SELECT MAX(p.page_no) FROM course_page p WHERE p.chapter_id = ch.id) AS page_max
            FROM lacking
            JOIN course_chapter ch ON ch.id = lacking.chapter_id
            """,
            (rs, rowNum) ->
                new KcChapterRow(
                    (Integer) rs.getObject("chapter_order"),
                    rs.getString("title"),
                    (Integer) rs.getObject("page_min"),
                    (Integer) rs.getObject("page_max"),
                    null),
            userId,
            afterOrder)
        .stream()
        .findFirst();
  }

  /** 学生当前第一个 ACTIVE 班级（按 classroom id 升序取第一个）。 */
  Optional<Long> findStudentPrimaryClassroom(Long userId) {
    return jdbcTemplate
        .query(
            """
            SELECT c.id
            FROM classrooms c
            JOIN classroom_members m ON m.classroom_id = c.id
            WHERE m.student_id = ? AND m.status = 'ACTIVE'
            ORDER BY c.id
            LIMIT 1
            """,
            (rs, rowNum) -> rs.getLong("id"),
            userId)
        .stream()
        .findFirst();
  }

  int countActiveMembers(Long classroomId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM classroom_members WHERE classroom_id = ? AND status = 'ACTIVE'",
            Integer.class,
            classroomId);
    return count == null ? 0 : count;
  }

  int countOnTrackMembers(Long classroomId, BigDecimal masteryThreshold) {
    Integer count =
        jdbcTemplate.queryForObject(
            """
            WITH per_user AS (
              SELECT m.user_id, AVG(m.mastery_score) AS avg_mastery
              FROM kc_mastery m
              JOIN classroom_members cm ON cm.student_id = m.user_id
              WHERE cm.classroom_id = ? AND cm.status = 'ACTIVE'
              GROUP BY m.user_id
            )
            SELECT COUNT(*) FROM per_user WHERE avg_mastery >= ?
            """,
            Integer.class,
            classroomId,
            masteryThreshold);
    return count == null ? 0 : count;
  }

  /** 班级最近 7 天新发布的活跃任务数。 */
  int countNewClassroomQuests(Long classroomId, OffsetDateTime since) {
    Integer count =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM classroom_quest
            WHERE classroom_id = ? AND status = 'ACTIVE' AND created_at >= ?
            """,
            Integer.class,
            classroomId,
            since);
    return count == null ? 0 : count;
  }

  /** 班级当前进行中的最终任务（按 due_date 最近）。 */
  Optional<Long> findClassFinalProblemId(Long classroomId) {
    return jdbcTemplate
        .query(
            """
            SELECT target_problem_id FROM classroom_quest
            WHERE classroom_id = ? AND status = 'ACTIVE' AND target_problem_id IS NOT NULL
            ORDER BY due_date NULLS LAST
            LIMIT 1
            """,
            (rs, rowNum) -> (Long) rs.getObject("target_problem_id"),
            classroomId)
        .stream()
        .filter(java.util.Objects::nonNull)
        .findFirst();
  }

  /** 班级周热点 KC：本周失败最多 KC。 */
  Optional<String> findClassWeeklyHotspot(
      Long classroomId, OffsetDateTime since, OffsetDateTime until) {
    return jdbcTemplate
        .query(
            """
            SELECT COALESCE(kc.kc_name, cp.kc_code) AS kc_name, COUNT(*) AS cnt
            FROM practice_submission ps
            JOIN classroom_members cm ON cm.student_id = ps.user_id AND cm.status = 'ACTIVE'
            JOIN judge_result jr ON jr.submission_id = ps.id
            JOIN course_problem cp ON cp.id = ps.problem_id
            LEFT JOIN course_kc kc ON kc.kc_code = cp.kc_code AND kc.pack_id = 43
            WHERE cm.classroom_id = ?
              AND ps.submitted_at >= ? AND ps.submitted_at < ?
              AND jr.verdict NOT IN ('AC')
            GROUP BY kc.kc_name, cp.kc_code
            ORDER BY cnt DESC
            LIMIT 1
            """,
            (rs, rowNum) -> rs.getString("kc_name"),
            classroomId,
            since,
            until)
        .stream()
        .findFirst();
  }

  /** 用户在班级 mastery 排名（1-based），用于段位划分。 */
  Optional<Integer> findStudentClassroomRank(Long classroomId, Long userId) {
    return jdbcTemplate
        .query(
            """
            WITH ranked AS (
              SELECT cm.student_id AS user_id,
                     COALESCE((SELECT AVG(mastery_score) FROM kc_mastery WHERE user_id = cm.student_id), 0) AS avg_mastery,
                     ROW_NUMBER() OVER (
                       ORDER BY COALESCE((SELECT AVG(mastery_score) FROM kc_mastery WHERE user_id = cm.student_id), 0) DESC,
                                cm.student_id ASC) AS rnk
              FROM classroom_members cm
              WHERE cm.classroom_id = ? AND cm.status = 'ACTIVE'
            )
            SELECT rnk FROM ranked WHERE user_id = ?
            """,
            (rs, rowNum) -> (Integer) rs.getObject("rnk"),
            classroomId,
            userId)
        .stream()
        .findFirst();
  }

  record KcChapterRow(Integer chapterOrder, String title, Integer pageMin, Integer pageMax, Integer examplePage) {}

  record KcFailureRow(String kcCode, String kcName, int failedCount) {}

  record VerdictCountRow(String verdict, int count) {}

  record LastFailureRow(Long problemId, String title, String verdict) {}

  record KcMasteryRow(String kcCode, String kcName, BigDecimal masteryScore) {}
}
