package com.pythontopia.classroom.dashboard.repository;

import com.pythontopia.classroom.dashboard.dto.ErrorHotspotDto;
import com.pythontopia.classroom.dashboard.dto.KcHeatmapEntryDto;
import com.pythontopia.classroom.dashboard.dto.QuestCompletionDto;
import com.pythontopia.classroom.dashboard.dto.RecentActivityDto;
import com.pythontopia.classroom.dashboard.dto.ResourceUsageDto;
import com.pythontopia.classroom.dashboard.dto.ResourceUsageDto.DailyBucket;
import com.pythontopia.classroom.dashboard.dto.ResourceUsageDto.ResourceTypeBucket;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 从既有事件表聚合班级看板数据。
 *
 * <p>调用方 {@link com.pythontopia.classroom.dashboard.service.ClassroomDashboardService}
 * 通过 classroom_kc_snapshot 表补充缓存。
 */
@Repository
public class ClassroomDashboardRepository {

  private final JdbcTemplate jdbcTemplate;

  public ClassroomDashboardRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /** KC 热力图取数（提为常量便于对「班级口径」做静态断言，见 ClassroomDashboardSqlContractTest）。 */
  static final String KC_HEATMAP_SQL =
        """
        WITH active_members AS (
          SELECT student_id FROM classroom_members
          WHERE classroom_id = ? AND status = 'ACTIVE'
        ),
        kc_rows AS (
          SELECT kc.kc_code, kc.kc_name, kc.chapter_id, kc.chapter_order
          FROM course_kc kc
          WHERE kc.pack_id = 43
        )
        SELECT kc.kc_code AS kc_code,
               kc.kc_name AS kc_name,
               kc.chapter_id AS chapter_id,
               kc.chapter_order AS chapter_order,
               -- 班级口径：分母固定为全班 ACTIVE 人数，没练过该 KC 的学生按 0 计。
               -- 用 AVG() 会跳过 NULL（只对已有 kc_mastery 行的学生取平均），30 人班里 2 人练到
               -- 0.95 就会把热力格涂成「已掌握 95%」，与底部「2/30 掌握」自相矛盾。
               COALESCE(
                 SUM(m.mastery_score) / NULLIF(COUNT(DISTINCT am.student_id), 0), 0
               ) AS avg_mastery,
               COUNT(DISTINCT am.student_id) AS student_count,
               COUNT(DISTINCT CASE WHEN m.mastery_score >= 0.8 THEN m.user_id END) AS mastered_count,
               COUNT(DISTINCT CASE WHEN m.mastery_score >= 0.3 AND m.mastery_score < 0.8 THEN m.user_id END) AS learning_count,
               COUNT(DISTINCT CASE WHEN m.mastery_score < 0.3 THEN m.user_id END) AS struggling_count
        FROM kc_rows kc
        CROSS JOIN active_members am
        LEFT JOIN kc_mastery m
          ON m.user_id = am.student_id AND m.kc_code = kc.kc_code
        GROUP BY kc.kc_code, kc.kc_name, kc.chapter_id, kc.chapter_order
        ORDER BY kc.chapter_order, kc.kc_code
        """;

  public List<KcHeatmapEntryDto> findKcHeatmap(Long classroomId) {
    return jdbcTemplate.query(
        KC_HEATMAP_SQL,
        (rs, rowNum) -> new KcHeatmapEntryDto(
            rs.getString("kc_code"),
            rs.getString("kc_name"),
            (Long) rs.getObject("chapter_id"),
            (Integer) rs.getObject("chapter_order"),
            rs.getBigDecimal("avg_mastery").setScale(3, RoundingMode.HALF_UP),
            rs.getInt("student_count"),
            rs.getInt("mastered_count"),
            rs.getInt("learning_count"),
            rs.getInt("struggling_count")),
        classroomId);
  }

  /**
   * 班级任务完成情况。分子分母必须同口径，否则会出现 completionRate &gt; 100%：
   * 分母是<b>当前</b> ACTIVE 成员数，分子若统计 source_ref_id 命中的全部 quest 行，就会把
   * 已被移出班级的学生也算进来（3 人全完成 → 移除 1 人 → 3/2 = 150%）。这里在 JOIN 上补
   * EXISTS 子查询，三个计数一律只看仍在班的成员。
   */
  static final String QUEST_SUMMARY_SQL =
        """
        SELECT cq.id AS classroom_quest_id,
               cq.title,
               cq.due_date,
               cq.status,
               (SELECT COUNT(*) FROM classroom_members cm
                 WHERE cm.classroom_id = cq.classroom_id AND cm.status = 'ACTIVE')
                 AS total_students,
               COUNT(q.*) FILTER (WHERE q.status = 'COMPLETED') AS completed_count,
               COUNT(q.*) FILTER (WHERE q.status = 'IN_PROGRESS') AS in_progress_count,
               COUNT(q.*) FILTER (WHERE q.status = 'SKIPPED') AS skipped_count
        FROM classroom_quest cq
        LEFT JOIN quest q
          ON q.source = 'CLASSROOM' AND q.source_ref_id = cq.id
         AND EXISTS (
               SELECT 1 FROM classroom_members cm2
               WHERE cm2.classroom_id = cq.classroom_id
                 AND cm2.student_id = q.user_id
                 AND cm2.status = 'ACTIVE')
        WHERE cq.classroom_id = ?
        GROUP BY cq.id, cq.title, cq.due_date, cq.status, cq.classroom_id
        ORDER BY cq.due_date DESC, cq.id DESC
        """;

  /**
   * 完成率 = 完成人数 / 当前 ACTIVE 成员数。分母为 0 时返回 0（旧实现用 {@code Math.max(1, total)}
   * 只防除零、不防倒挂，配合「分子含已移出学生」会算出 150%）。
   */
  static BigDecimal completionRate(int completed, int totalStudents) {
    if (totalStudents <= 0) {
      return BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
    }
    return BigDecimal.valueOf(completed)
        .divide(BigDecimal.valueOf(totalStudents), 3, RoundingMode.HALF_UP);
  }

  public List<QuestCompletionDto> findQuestSummary(Long classroomId) {
    return jdbcTemplate.query(
        QUEST_SUMMARY_SQL,
        (rs, rowNum) -> {
          int total = rs.getInt("total_students");
          int completed = rs.getInt("completed_count");
          BigDecimal rate = completionRate(completed, total);
          return new QuestCompletionDto(
              rs.getLong("classroom_quest_id"),
              rs.getString("title"),
              rs.getObject("due_date", LocalDate.class),
              rs.getString("status"),
              rs.getInt("total_students"),
              completed,
              rs.getInt("in_progress_count"),
              rs.getInt("skipped_count"),
              rate);
        },
        classroomId);
  }

  public List<ErrorHotspotDto> findErrorHotspots(Long classroomId, int recentDays) {
    OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minusDays(recentDays);
    List<RawHotspot> rows = jdbcTemplate.query(
        """
        SELECT cp.kc_code,
               kc.kc_name,
               COUNT(*) AS failed_count,
               COUNT(DISTINCT ps.user_id) AS affected_students,
               COALESCE(jr.verdict, 'UNKNOWN') AS verdict
        FROM practice_submission ps
        JOIN classroom_members cm ON cm.student_id = ps.user_id AND cm.status = 'ACTIVE'
        JOIN judge_result jr ON jr.submission_id = ps.id
        JOIN course_problem cp ON cp.id = ps.problem_id
        LEFT JOIN course_kc kc ON kc.kc_code = cp.kc_code AND kc.pack_id = 43
        WHERE cm.classroom_id = ?
          AND jr.verdict NOT IN ('AC')
          AND ps.submitted_at >= ?
        GROUP BY cp.kc_code, kc.kc_name, jr.verdict
        ORDER BY failed_count DESC
        """,
        (rs, rowNum) -> new RawHotspot(
            rs.getString("kc_code"),
            rs.getString("kc_name"),
            rs.getString("verdict"),
            rs.getInt("failed_count"),
            rs.getInt("affected_students")),
        classroomId,
        java.sql.Timestamp.from(since.toInstant()));

    Map<String, AggregatedHotspot> grouped = new HashMap<>();
    for (RawHotspot row : rows) {
      AggregatedHotspot acc = grouped.computeIfAbsent(
          row.kcCode(),
          key -> new AggregatedHotspot(row.kcCode(), row.kcName()));
      acc.totalFailures += row.failedCount();
      acc.affectedStudents = Math.max(acc.affectedStudents, row.affectedStudents());
      acc.categories.merge(row.verdict(), row.failedCount(), Integer::sum);
    }

    List<ErrorHotspotDto> result = new ArrayList<>();
    grouped.values().stream()
        .sorted((a, b) -> Integer.compare(b.totalFailures, a.totalFailures))
        .limit(10)
        .forEach(acc -> {
          List<ErrorHotspotDto.ErrorCategoryDto> cats = new ArrayList<>();
          acc.categories.forEach((k, v) -> cats.add(new ErrorHotspotDto.ErrorCategoryDto(k, v)));
          cats.sort((a, b) -> Integer.compare(b.count(), a.count()));
          result.add(new ErrorHotspotDto(
              acc.kcCode, acc.kcName, acc.totalFailures, acc.affectedStudents, cats));
        });
    return result;
  }

  public ResourceUsageDto findResourceUsage(Long classroomId, int recentDays) {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    LocalDate start = today.minusDays(recentDays - 1L);
    List<ResourceTypeBucket> totals = jdbcTemplate.query(
        """
        SELECT lr.type AS resource_type, COUNT(*) AS cnt
        FROM learning_resource lr
        JOIN classroom_members cm ON cm.student_id = lr.user_id AND cm.status = 'ACTIVE'
        WHERE cm.classroom_id = ?
          AND lr.created_at >= ?::date
        GROUP BY lr.type
        ORDER BY cnt DESC
        """,
        (rs, rowNum) -> new ResourceTypeBucket(rs.getString("resource_type"), rs.getInt("cnt")),
        classroomId,
        java.sql.Date.valueOf(start));
    List<DailyBucket> daily = jdbcTemplate.query(
        """
        SELECT (lr.created_at AT TIME ZONE 'UTC')::date AS day,
               lr.type AS resource_type,
               COUNT(*) AS cnt
        FROM learning_resource lr
        JOIN classroom_members cm ON cm.student_id = lr.user_id AND cm.status = 'ACTIVE'
        WHERE cm.classroom_id = ?
          AND lr.created_at >= ?::date
        GROUP BY day, lr.type
        ORDER BY day, lr.type
        """,
        (rs, rowNum) -> new DailyBucket(
            rs.getObject("day", LocalDate.class),
            rs.getString("resource_type"),
            rs.getInt("cnt")),
        classroomId,
        java.sql.Date.valueOf(start));
    return new ResourceUsageDto(start, today, totals, daily);
  }

  /**
   * 最近活跃事件流（近 {@code recentDays} 天）。
   *
   * <p>三路 UNION ALL 原本没有任何时间下界，外层 LIMIT 只能在全量排序后才生效——班级跑一学期后
   * 每次打开概览都要全表扫描 + 排序。这里与 {@link #findErrorHotspots} / {@link #findResourceUsage}
   * 统一到同一个 7 天窗口，并让每个子查询各自 {@code ORDER BY ... LIMIT}，把排序成本压在索引上。
   */
  public List<RecentActivityDto> findRecentActivity(Long classroomId, int limit, int recentDays) {
    int safeLimit = Math.max(1, Math.min(50, limit));
    OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minusDays(recentDays);
    java.sql.Timestamp sinceTs = java.sql.Timestamp.from(since.toInstant());
    return jdbcTemplate.query(
        """
        SELECT * FROM (
          (SELECT u.id AS student_id, u.display_name, 'KC_MASTERY' AS event_kind,
                  e.kc_code AS kc_code, e.source AS detail, e.created_at AS occurred_at
           FROM kc_mastery_event e
           JOIN classroom_members cm ON cm.student_id = e.user_id AND cm.status = 'ACTIVE'
           JOIN users u ON u.id = e.user_id
           WHERE cm.classroom_id = ? AND e.created_at >= ?
           ORDER BY e.created_at DESC
           LIMIT ?)
          UNION ALL
          (SELECT u.id, u.display_name, 'SUBMISSION' AS event_kind,
                  cp.kc_code AS kc_code, COALESCE(jr.verdict, 'PENDING') AS detail,
                  ps.submitted_at AS occurred_at
           FROM practice_submission ps
           JOIN classroom_members cm ON cm.student_id = ps.user_id AND cm.status = 'ACTIVE'
           JOIN users u ON u.id = ps.user_id
           JOIN course_problem cp ON cp.id = ps.problem_id
           LEFT JOIN judge_result jr ON jr.submission_id = ps.id
           WHERE cm.classroom_id = ? AND ps.submitted_at >= ?
           ORDER BY ps.submitted_at DESC
           LIMIT ?)
          UNION ALL
          (SELECT u.id, u.display_name, 'RESOURCE' AS event_kind,
                  NULL AS kc_code, lr.type AS detail, lr.created_at AS occurred_at
           FROM learning_resource lr
           JOIN classroom_members cm ON cm.student_id = lr.user_id AND cm.status = 'ACTIVE'
           JOIN users u ON u.id = lr.user_id
           WHERE cm.classroom_id = ? AND lr.created_at >= ?
           ORDER BY lr.created_at DESC
           LIMIT ?)
        ) merged
        ORDER BY occurred_at DESC
        LIMIT ?
        """,
        (rs, rowNum) -> new RecentActivityDto(
            rs.getLong("student_id"),
            rs.getString("display_name"),
            rs.getString("event_kind"),
            rs.getString("kc_code"),
            rs.getString("detail"),
            offset(rs, "occurred_at")),
        classroomId,
        sinceTs,
        safeLimit,
        classroomId,
        sinceTs,
        safeLimit,
        classroomId,
        sinceTs,
        safeLimit,
        safeLimit);
  }

  /**
   * 批量刷新班级 KC 快照：一条多值 INSERT ... ON CONFLICT 覆盖全部 65 个 KC，
   * 替换原来「每个 KC 一次往返」的 65 条 upsert。
   *
   * <p>同时修正列语义错位：{@code passed_count / learning_count / failed_count} 与
   * {@code masteredCount / learningCount / strugglingCount} 一一对应（原实现直接丢掉
   * learningCount，导致快照表里 passed + failed ≠ student_count）。
   */
  public int upsertSnapshots(Long classroomId, List<KcHeatmapEntryDto> entries) {
    if (entries == null || entries.isEmpty()) {
      return 0;
    }
    String values =
        entries.stream()
            .map(entry -> "(?, ?, ?, ?, ?, ?, ?, '[]'::jsonb)")
            .collect(java.util.stream.Collectors.joining(", "));
    List<Object> args = new ArrayList<>(entries.size() * 7);
    for (KcHeatmapEntryDto entry : entries) {
      args.add(classroomId);
      args.add(entry.kcCode());
      args.add(entry.avgMastery());
      args.add(entry.studentCount());
      args.add(entry.masteredCount());
      args.add(entry.learningCount());
      args.add(entry.strugglingCount());
    }
    return jdbcTemplate.update(
        """
        INSERT INTO classroom_kc_snapshot (
          classroom_id, kc_code, avg_mastery, student_count,
          passed_count, learning_count, failed_count, top_error_categories
        ) VALUES """
            + values
            + """

        ON CONFLICT (classroom_id, kc_code) DO UPDATE SET
          avg_mastery = EXCLUDED.avg_mastery,
          student_count = EXCLUDED.student_count,
          passed_count = EXCLUDED.passed_count,
          learning_count = EXCLUDED.learning_count,
          failed_count = EXCLUDED.failed_count,
          computed_at = now()
        """,
        args.toArray());
  }

  private OffsetDateTime offset(ResultSet rs, String column) throws SQLException {
    java.sql.Timestamp ts = rs.getTimestamp(column);
    return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
  }

  // 暂未使用，保留用于诊断时读取原始数组；不属于对外看板 API。
  @SuppressWarnings("unused")
  private List<Long> readBigintArray(Array array) throws SQLException {
    if (array == null) {
      return List.of();
    }
    Object raw = array.getArray();
    if (raw instanceof Object[] values) {
      List<Long> out = new ArrayList<>(values.length);
      for (Object v : values) {
        if (v == null) {
          continue;
        }
        out.add(Long.valueOf(v.toString()));
      }
      return out;
    }
    return List.of();
  }

  private record RawHotspot(
      String kcCode, String kcName, String verdict, int failedCount, int affectedStudents) {}

  private static final class AggregatedHotspot {
    final String kcCode;
    final String kcName;
    int totalFailures;
    int affectedStudents;
    final Map<String, Integer> categories = new HashMap<>();

    AggregatedHotspot(String kcCode, String kcName) {
      this.kcCode = kcCode;
      this.kcName = kcName;
    }
  }
}
