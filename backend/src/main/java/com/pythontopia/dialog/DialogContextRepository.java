package com.pythontopia.dialog;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * R-12 W2-NPC 对话上下文聚合查询。
 *
 * <p>所有 SQL 都按 (user_id 范围 / classroom_id 范围) 做了 owner filter，禁止跨用户跨班级泄露；
 * 各方法返回值统一用 {@link Optional} 或空集合，调用方（{@link DialogContextService}）负责兜底，
 * 不把空数据当异常抛。
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

  int countDistinctPassedProblems(Long userId) {
    Integer count =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(DISTINCT problem_id) FROM practice_submission
            WHERE user_id = ? AND status = 'PASSED'
            """,
            Integer.class,
            userId);
    return count == null ? 0 : count;
  }

  /** Total problems attempted + total passed (for first-attempt rate proxy). */
  int countAllSubmissions(Long userId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM practice_submission WHERE user_id = ?",
            Integer.class,
            userId);
    return count == null ? 0 : count;
  }

  int countPassedSubmissions(Long userId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM practice_submission WHERE user_id = ? AND status = 'PASSED'",
            Integer.class,
            userId);
    return count == null ? 0 : count;
  }

  /** KC code -> kc_name lookup limited to course pack 43. */
  Optional<String> findKcName(String kcCode) {
    if (kcCode == null || kcCode.isBlank()) {
      return Optional.empty();
    }
    try {
      String name =
          jdbcTemplate.queryForObject(
              "SELECT kc_name FROM course_kc WHERE pack_id = 43 AND kc_code = ?",
              String.class,
              kcCode);
      return Optional.ofNullable(name);
    } catch (EmptyResultDataAccessException ignored) {
      return Optional.empty();
    }
  }

  /** kc 的章节 order + chapter title + page range（最低与最高 page_no）。 */
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

  int countUnlockedCropsInRange(Long userId, OffsetDateTime since, OffsetDateTime until) {
    Integer count =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM crop_tile_state
            WHERE user_id = ? AND pack_id = 43 AND unlocked = TRUE
              AND unlocked_at IS NOT NULL
              AND unlocked_at >= ? AND unlocked_at < ?
            """,
            Integer.class,
            userId,
            since,
            until);
    return count == null ? 0 : count;
  }

  /** 已解锁、未成熟的下一个 KC（按章节序后第一个未解锁的 KC）。 */
  Optional<String> findNextLockedKc(Long userId) {
    return jdbcTemplate
        .query(
            """
            SELECT COALESCE(kc.kc_name, cts.kc_code) AS kc_name
            FROM crop_tile_state cts
            LEFT JOIN course_kc kc ON kc.kc_code = cts.kc_code AND kc.pack_id = 43
            WHERE cts.user_id = ? AND cts.pack_id = 43 AND cts.unlocked = FALSE
            ORDER BY COALESCE(kc.chapter_order, 99), cts.kc_code
            LIMIT 1
            """,
            (rs, rowNum) -> rs.getString("kc_name"),
            userId)
        .stream()
        .findFirst();
  }

  Optional<String> findLatestUnlockedKcName(Long userId) {
    return jdbcTemplate
        .query(
            """
            SELECT COALESCE(kc.kc_name, cts.kc_code) AS kc_name
            FROM crop_tile_state cts
            LEFT JOIN course_kc kc ON kc.kc_code = cts.kc_code AND kc.pack_id = 43
            WHERE cts.user_id = ? AND cts.pack_id = 43 AND cts.unlocked = TRUE
              AND cts.unlocked_at IS NOT NULL
            ORDER BY cts.unlocked_at DESC
            LIMIT 1
            """,
            (rs, rowNum) -> rs.getString("kc_name"),
            userId)
        .stream()
        .findFirst();
  }

  Optional<String> findWitheredKcName(Long userId) {
    return jdbcTemplate
        .query(
            """
            SELECT COALESCE(kc.kc_name, cts.kc_code) AS kc_name
            FROM crop_tile_state cts
            LEFT JOIN course_kc kc ON kc.kc_code = cts.kc_code AND kc.pack_id = 43
            WHERE cts.user_id = ? AND cts.pack_id = 43 AND cts.status = 'WITHERED'
            ORDER BY cts.updated_at DESC
            LIMIT 1
            """,
            (rs, rowNum) -> rs.getString("kc_name"),
            userId)
        .stream()
        .findFirst();
  }

  Optional<String> findHarvestKcName(Long userId) {
    return jdbcTemplate
        .query(
            """
            SELECT COALESCE(kc.kc_name, cts.kc_code) AS kc_name
            FROM crop_tile_state cts
            LEFT JOIN course_kc kc ON kc.kc_code = cts.kc_code AND kc.pack_id = 43
            WHERE cts.user_id = ? AND cts.pack_id = 43 AND cts.status = 'MATURE'
            ORDER BY cts.mastery_score DESC
            LIMIT 1
            """,
            (rs, rowNum) -> rs.getString("kc_name"),
            userId)
        .stream()
        .findFirst();
  }

  /** 已解锁 KC 名（前 N 个，按章节顺序）。 */
  List<String> findUnlockedKcNames(Long userId, int limit) {
    return jdbcTemplate.query(
        """
        SELECT COALESCE(kc.kc_name, cts.kc_code) AS kc_name
        FROM crop_tile_state cts
        LEFT JOIN course_kc kc ON kc.kc_code = cts.kc_code AND kc.pack_id = 43
        WHERE cts.user_id = ? AND cts.pack_id = 43 AND cts.unlocked = TRUE
        ORDER BY COALESCE(kc.chapter_order, 99), cts.kc_code
        LIMIT ?
        """,
        (rs, rowNum) -> rs.getString("kc_name"),
        userId,
        limit);
  }

  /** 全用户 mastery 排序后取最低，用于 lowestMasteryKcName。 */
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

  /** 本周 mastery delta 合计（用于 masteryDelta）。 */
  Optional<BigDecimal> findMasteryDeltaSum(
      Long userId, OffsetDateTime since, OffsetDateTime until) {
    return Optional.ofNullable(
        jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(SUM(delta), 0)
            FROM kc_mastery_event
            WHERE user_id = ? AND created_at >= ? AND created_at < ?
            """,
            BigDecimal.class,
            userId,
            since,
            until));
  }

  /** 用户当前最高 mastery KC 所在章节 order（currentChapterOrder 用）。 */
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

  /** entry problem：beginner 难度第一个题号。 */
  Optional<Long> findEntryProblemId() {
    try {
      return Optional.ofNullable(
          jdbcTemplate.queryForObject(
              """
              SELECT id FROM course_problem
              WHERE pack_id = 43 AND difficulty = 'BEGINNER'
              ORDER BY id LIMIT 1
              """,
              Long.class));
    } catch (EmptyResultDataAccessException ignored) {
      return Optional.empty();
    }
  }

  /** 推荐题：lowestMasteryKc 对应 KC 的第一道题，没有则任意 INTERMEDIATE 题。 */
  Optional<ProblemRow> findRecommendProblem(String kcCode) {
    if (kcCode != null && !kcCode.isBlank()) {
      List<ProblemRow> hit =
          jdbcTemplate.query(
              """
              SELECT id, difficulty FROM course_problem
              WHERE pack_id = 43 AND kc_code = ?
              ORDER BY difficulty, id
              LIMIT 1
              """,
              (rs, rowNum) -> new ProblemRow(rs.getLong("id"), rs.getString("difficulty")),
              kcCode);
      if (!hit.isEmpty()) {
        return Optional.of(hit.get(0));
      }
    }
    return jdbcTemplate
        .query(
            """
            SELECT id, difficulty FROM course_problem
            WHERE pack_id = 43 AND difficulty = 'INTERMEDIATE'
            ORDER BY id LIMIT 1
            """,
            (rs, rowNum) -> new ProblemRow(rs.getLong("id"), rs.getString("difficulty")))
        .stream()
        .findFirst();
  }

  Optional<Long> findChallengeProblemId() {
    try {
      return Optional.ofNullable(
          jdbcTemplate.queryForObject(
              """
              SELECT id FROM course_problem
              WHERE pack_id = 43 AND difficulty = 'ADVANCED'
              ORDER BY id LIMIT 1
              """,
              Long.class));
    } catch (EmptyResultDataAccessException ignored) {
      return Optional.empty();
    }
  }

  /** 按难度 + dayIndex 取整 hash 出每日题号，保证同一天对同一玩家稳定。 */
  Optional<Long> findDailyProblemByDifficulty(String difficulty, int seed) {
    List<Long> ids =
        jdbcTemplate.query(
            "SELECT id FROM course_problem WHERE pack_id = 43 AND difficulty = ? ORDER BY id",
            (rs, rowNum) -> rs.getLong("id"),
            difficulty);
    if (ids.isEmpty()) {
      return Optional.empty();
    }
    int index = Math.floorMod(seed, ids.size());
    return Optional.of(ids.get(index));
  }

  /** 取用户当前所在第一个 ACTIVE 班级（学生视角，按 join 时间最早）。 */
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

  /** 班级中本周练习最多的同学（除自己之外）。 */
  Optional<PeerRow> findMostDiligentPeer(
      Long classroomId, Long excludeUserId, OffsetDateTime since, OffsetDateTime until) {
    return jdbcTemplate
        .query(
            """
            WITH peer_stats AS (
              SELECT ps.user_id, COUNT(*) AS exercises
              FROM practice_submission ps
              JOIN classroom_members cm ON cm.student_id = ps.user_id AND cm.status = 'ACTIVE'
              WHERE cm.classroom_id = ?
                AND ps.user_id <> ?
                AND ps.submitted_at >= ? AND ps.submitted_at < ?
              GROUP BY ps.user_id
              ORDER BY exercises DESC
              LIMIT 1
            )
            SELECT ps.user_id, ps.exercises,
                   COALESCE((SELECT AVG(mastery_score) FROM kc_mastery WHERE user_id = ps.user_id), 0) AS avg_mastery
            FROM peer_stats ps
            """,
            (rs, rowNum) ->
                new PeerRow(
                    rs.getLong("user_id"),
                    rs.getInt("exercises"),
                    rs.getBigDecimal("avg_mastery")),
            classroomId,
            excludeUserId,
            since,
            until)
        .stream()
        .findFirst();
  }

  /** 班级中刚起步同学（mastery 较低的）。 */
  Optional<PeerRow> findLateStarterPeer(
      Long classroomId, Long excludeUserId, OffsetDateTime monthSince) {
    return jdbcTemplate
        .query(
            """
            WITH base AS (
              SELECT cm.student_id AS user_id,
                     COALESCE(
                       (SELECT AVG(mastery_score) FROM kc_mastery WHERE user_id = cm.student_id),
                       0) AS avg_mastery,
                     (SELECT COUNT(*) FROM practice_submission ps2
                      WHERE ps2.user_id = cm.student_id AND ps2.submitted_at >= ?) AS month_exercises
              FROM classroom_members cm
              WHERE cm.classroom_id = ? AND cm.status = 'ACTIVE' AND cm.student_id <> ?
            )
            SELECT user_id, month_exercises, avg_mastery
            FROM base
            WHERE avg_mastery > 0
            ORDER BY avg_mastery ASC
            LIMIT 1
            """,
            (rs, rowNum) ->
                new PeerRow(
                    rs.getLong("user_id"),
                    rs.getInt("month_exercises"),
                    rs.getBigDecimal("avg_mastery")),
            monthSince,
            classroomId,
            excludeUserId)
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

  /** world_session 上的 energy / energy_cap（R-12 W5 加的列）；不存在或 SQL 失败时返回 empty。 */
  Optional<EnergyRow> findEnergy(Long userId) {
    try {
      return jdbcTemplate
          .query(
              """
              SELECT energy, energy_cap FROM world_session
              WHERE user_id = ?
              ORDER BY id DESC
              LIMIT 1
              """,
              (rs, rowNum) -> new EnergyRow(rs.getInt("energy"), rs.getInt("energy_cap")),
              userId)
          .stream()
          .findFirst();
    } catch (org.springframework.jdbc.BadSqlGrammarException ignored) {
      // 老库可能没跑 V115，静默返回 empty 让 Service 走 100/100 默认。
      return Optional.empty();
    }
  }

  /** 用户最近 24h 是否给某 NPC 送过 like/love 礼物（giftJustGiven 判定）。 */
  boolean hasFavoriteGiftRecently(Long userId, String npcKey, int hoursWindow) {
    if (npcKey == null || npcKey.isBlank()) {
      return false;
    }
    try {
      Integer count =
          jdbcTemplate.queryForObject(
              """
              SELECT COUNT(*) FROM npc_gift_log
              WHERE user_id = ? AND npc_key = ?
                AND gift_tier IN ('like', 'love')
                AND given_at >= now() - make_interval(hours => ?)
              """,
              Integer.class,
              userId,
              npcKey,
              hoursWindow);
      return count != null && count > 0;
    } catch (org.springframework.jdbc.BadSqlGrammarException ignored) {
      return false;
    }
  }

  record KcChapterRow(Integer chapterOrder, String title, Integer pageMin, Integer pageMax, Integer examplePage) {}

  record KcFailureRow(String kcCode, String kcName, int failedCount) {}

  record VerdictCountRow(String verdict, int count) {}

  record LastFailureRow(Long problemId, String title, String verdict) {}

  record KcMasteryRow(String kcCode, String kcName, BigDecimal masteryScore) {}

  record ProblemRow(Long id, String difficulty) {}

  record PeerRow(Long userId, int metric, BigDecimal avgMastery) {}

  record EnergyRow(int current, int max) {}
}
