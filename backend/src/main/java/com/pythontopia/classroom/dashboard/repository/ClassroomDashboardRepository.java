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
 * Aggregates classroom dashboard data from existing event tables.
 *
 * <p>Caller is the {@link com.pythontopia.classroom.dashboard.service.ClassroomDashboardService},
 * which adds caching via the classroom_kc_snapshot table.
 */
@Repository
public class ClassroomDashboardRepository {

  private final JdbcTemplate jdbcTemplate;

  public ClassroomDashboardRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<KcHeatmapEntryDto> findKcHeatmap(Long classroomId) {
    return jdbcTemplate.query(
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
               COALESCE(AVG(m.mastery_score), 0) AS avg_mastery,
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
        """,
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

  public List<QuestCompletionDto> findQuestSummary(Long classroomId) {
    return jdbcTemplate.query(
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
        WHERE cq.classroom_id = ?
        GROUP BY cq.id, cq.title, cq.due_date, cq.status, cq.classroom_id
        ORDER BY cq.due_date DESC, cq.id DESC
        """,
        (rs, rowNum) -> {
          int total = Math.max(1, rs.getInt("total_students"));
          int completed = rs.getInt("completed_count");
          BigDecimal rate = BigDecimal.valueOf(completed)
              .divide(BigDecimal.valueOf(total), 3, RoundingMode.HALF_UP);
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

  public List<RecentActivityDto> findRecentActivity(Long classroomId, int limit) {
    int safeLimit = Math.max(1, Math.min(50, limit));
    return jdbcTemplate.query(
        """
        SELECT * FROM (
          SELECT u.id AS student_id, u.display_name, 'KC_MASTERY' AS event_kind,
                 e.kc_code AS kc_code, e.source AS detail, e.created_at AS occurred_at
          FROM kc_mastery_event e
          JOIN classroom_members cm ON cm.student_id = e.user_id AND cm.status = 'ACTIVE'
          JOIN users u ON u.id = e.user_id
          WHERE cm.classroom_id = ?
          UNION ALL
          SELECT u.id, u.display_name, 'SUBMISSION' AS event_kind,
                 cp.kc_code AS kc_code, COALESCE(jr.verdict, 'PENDING') AS detail,
                 ps.submitted_at AS occurred_at
          FROM practice_submission ps
          JOIN classroom_members cm ON cm.student_id = ps.user_id AND cm.status = 'ACTIVE'
          JOIN users u ON u.id = ps.user_id
          JOIN course_problem cp ON cp.id = ps.problem_id
          LEFT JOIN judge_result jr ON jr.submission_id = ps.id
          WHERE cm.classroom_id = ?
          UNION ALL
          SELECT u.id, u.display_name, 'RESOURCE' AS event_kind,
                 NULL AS kc_code, lr.type AS detail, lr.created_at AS occurred_at
          FROM learning_resource lr
          JOIN classroom_members cm ON cm.student_id = lr.user_id AND cm.status = 'ACTIVE'
          JOIN users u ON u.id = lr.user_id
          WHERE cm.classroom_id = ?
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
        classroomId,
        classroomId,
        safeLimit);
  }

  public void upsertSnapshot(Long classroomId, KcHeatmapEntryDto entry) {
    jdbcTemplate.update(
        """
        INSERT INTO classroom_kc_snapshot (
          classroom_id, kc_code, avg_mastery, student_count, passed_count, failed_count, top_error_categories
        ) VALUES (?, ?, ?, ?, ?, ?, '[]'::jsonb)
        ON CONFLICT (classroom_id, kc_code) DO UPDATE SET
          avg_mastery = EXCLUDED.avg_mastery,
          student_count = EXCLUDED.student_count,
          passed_count = EXCLUDED.passed_count,
          failed_count = EXCLUDED.failed_count,
          computed_at = now()
        """,
        classroomId,
        entry.kcCode(),
        entry.avgMastery(),
        entry.studentCount(),
        entry.masteredCount(),
        entry.strugglingCount());
  }

  private OffsetDateTime offset(ResultSet rs, String column) throws SQLException {
    java.sql.Timestamp ts = rs.getTimestamp(column);
    return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
  }

  // Unused but kept for diagnostic-friendly raw access; not part of the public dashboard API.
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
