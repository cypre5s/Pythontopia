package com.pythontopia.learner.effect;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/** Test-only deterministic simulator for long-term classroom learning-effect analytics. */
final class ClassroomLearningEffectSimulator {

  private static final int COHORT_SIZE = 12;
  private static final int WEEK_COUNT = 6;
  private static final int KC_COUNT = 5;
  private static final String SOURCE = "long-term-sim";

  private final JdbcTemplate jdbc;

  ClassroomLearningEffectSimulator(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  LongTermSimulationReport runSixWeekSimulation() {
    String runId = "ltsim-" + System.nanoTime();
    List<String> kcCodes =
        jdbc.queryForList(
            "SELECT kc_code FROM course_kc WHERE pack_id = 43 ORDER BY chapter_order, kc_code LIMIT ?",
            String.class,
            KC_COUNT);
    if (kcCodes.size() < KC_COUNT) {
      throw new IllegalStateException("course pack 43 KC seed is incomplete");
    }

    Long teacherId = insertUser("teacher-" + runId, "Long Term Teacher", "TEACHER");
    Long classroomId = insertClassroom(teacherId, runId);
    List<Long> baselineStudents = insertCohort(classroomId, runId, "baseline");
    List<Long> treatmentStudents = insertCohort(classroomId, runId, "treatment");

    OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).minusWeeks(WEEK_COUNT);
    seedCohort(runId, "baseline", baselineStudents, kcCodes, start);
    seedCohort(runId, "treatment", treatmentStudents, kcCodes, start);

    return new LongTermSimulationReport(
        classroomId,
        aggregate(runId, classroomId, "baseline"),
        aggregate(runId, classroomId, "treatment"),
        rawLogCounts(runId));
  }

  private Long insertUser(String username, String displayName, String role) {
    return jdbc.queryForObject(
        """
        INSERT INTO users (username, display_name, password_hash, role)
        VALUES (?, ?, 'x', ?) RETURNING id
        """,
        Long.class,
        username,
        displayName,
        role);
  }

  private Long insertClassroom(Long teacherId, String runId) {
    String joinCode = uniqueJoinCode(runId);
    return jdbc.queryForObject(
        """
        INSERT INTO classrooms (name, description, teacher_id, pack_id, join_code)
        VALUES (?, 'long-term simulation', ?, 43, ?) RETURNING id
        """,
        Long.class,
        "Long Term Simulation " + runId,
        teacherId,
        joinCode);
  }

  private List<Long> insertCohort(Long classroomId, String runId, String cohort) {
    List<Long> ids = new ArrayList<>();
    for (int index = 0; index < COHORT_SIZE; index++) {
      Long userId =
          insertUser(
              cohort.substring(0, 1) + "-" + runId + "-" + index,
              cohort + " student " + index,
              "STUDENT");
      jdbc.update(
          "INSERT INTO classroom_members (classroom_id, student_id, status) VALUES (?, ?, 'ACTIVE')",
          classroomId,
          userId);
      ids.add(userId);
    }
    return ids;
  }

  private void seedCohort(
      String runId,
      String cohort,
      List<Long> students,
      List<String> kcCodes,
      OffsetDateTime start) {
    for (int studentIndex = 0; studentIndex < students.size(); studentIndex++) {
      Long userId = students.get(studentIndex);
      Map<String, Double> masteryByKc = new HashMap<>();
      Map<String, Integer> repsByKc = new HashMap<>();
      Map<String, Integer> lapsesByKc = new HashMap<>();
      for (String kcCode : kcCodes) {
        masteryByKc.put(kcCode, 0.20);
        repsByKc.put(kcCode, 0);
        lapsesByKc.put(kcCode, 0);
        upsertMastery(userId, kcCode, 0.20, 0.30, start);
      }

      for (int week = 1; week <= WEEK_COUNT; week++) {
        OffsetDateTime weekAt = start.plusWeeks(week);
        for (int resourceIndex = 0; resourceIndex < 2; resourceIndex++) {
          Long resourceId =
              insertResource(runId, cohort, userId, kcCodes.get((week + resourceIndex) % kcCodes.size()), weekAt);
          if (shouldConsumeResource(cohort, studentIndex, week, resourceIndex)) {
            insertEvent(
                runId,
                cohort,
                userId,
                "resource_consumed",
                "{\"resourceId\":"
                    + resourceId
                    + ",\"kcCode\":\""
                    + kcCodes.get((week + resourceIndex) % kcCodes.size())
                    + "\",\"resourceKind\":\"HANDOUT\"}",
                1,
                weekAt.plusHours(resourceIndex));
          }
        }

        for (int kcIndex = 0; kcIndex < kcCodes.size(); kcIndex++) {
          String kcCode = kcCodes.get(kcIndex);
          boolean completed = reviewCompleted(cohort, studentIndex, week, kcIndex);
          insertEvent(
              runId,
              cohort,
              userId,
              "review_scheduled",
              "{\"kcCode\":\"" + kcCode + "\",\"week\":" + week + "}",
              2,
              weekAt.plusHours(kcIndex));
          if (completed) {
            insertEvent(
                runId,
                cohort,
                userId,
                "review_completed",
                "{\"kcCode\":\"" + kcCode + "\",\"week\":" + week + "}",
                4,
                weekAt.plusHours(kcIndex).plusMinutes(30));
          }

          double delta = masteryDelta(cohort, completed);
          double nextMastery = clamp(masteryByKc.get(kcCode) + delta);
          masteryByKc.put(kcCode, nextMastery);
          int reps = repsByKc.get(kcCode) + (completed ? 1 : 0);
          int lapses = lapsesByKc.get(kcCode) + (completed ? 0 : 1);
          repsByKc.put(kcCode, reps);
          lapsesByKc.put(kcCode, lapses);
          String source = completed ? "PRACTICE_PASS" : "RESOURCE_READ";
          insertMasteryEvent(runId, cohort, userId, kcCode, source, delta, weekAt.plusHours(kcIndex));
          upsertMastery(userId, kcCode, nextMastery, Math.min(0.95, 0.30 + week * 0.08), weekAt);
          upsertReviewState(userId, kcCode, nextMastery, reps, lapses, weekAt);
        }
      }
    }
  }

  private Long insertResource(
      String runId, String cohort, Long userId, String kcCode, OffsetDateTime createdAt) {
    return jdbc.queryForObject(
        """
        INSERT INTO learning_resource (
          user_id, pack_id, kc_codes, type, title, content_format, content, evidence_page_ids,
          safety_status, grounding_status, created_at
        ) VALUES (?, 43, ARRAY[?]::text[], 'HANDOUT', ?,
          'STRUCT_JSON', ?::jsonb, ARRAY[10707]::bigint[], 'PASSED', 'PASSED', ?) RETURNING id
        """,
        Long.class,
        userId,
        kcCode,
        "long-term-sim " + runId + " " + cohort + " " + kcCode,
        "{\"simRun\":\"" + runId + "\",\"cohort\":\"" + cohort + "\"}",
        timestamp(createdAt));
  }

  private void insertEvent(
      String runId,
      String cohort,
      Long userId,
      String eventType,
      String extraPayload,
      int importance,
      OffsetDateTime createdAt) {
    String payload =
        extraPayload.substring(0, extraPayload.length() - 1)
            + ",\"simRun\":\""
            + runId
            + "\",\"cohort\":\""
            + cohort
            + "\"}";
    jdbc.update(
        """
        INSERT INTO learning_event_log (user_id, event_type, payload, source, importance, created_at)
        VALUES (?, ?, ?::jsonb, ?, ?, ?)
        """,
        userId,
        eventType,
        payload,
        SOURCE,
        importance,
        timestamp(createdAt));
  }

  private void insertMasteryEvent(
      String runId,
      String cohort,
      Long userId,
      String kcCode,
      String source,
      double delta,
      OffsetDateTime createdAt) {
    jdbc.update(
        """
        INSERT INTO kc_mastery_event (user_id, kc_code, source, delta, reason, created_at)
        VALUES (?, ?, ?, ?, ?, ?)
        """,
        userId,
        kcCode,
        source,
        delta,
        "long-term-sim:" + runId + ":" + cohort,
        timestamp(createdAt));
  }

  private void upsertMastery(
      Long userId, String kcCode, double mastery, double confidence, OffsetDateTime occurredAt) {
    jdbc.update(
        """
        INSERT INTO kc_mastery (user_id, kc_code, mastery_score, confidence, last_event_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT (user_id, kc_code) DO UPDATE SET
          mastery_score = EXCLUDED.mastery_score,
          confidence = EXCLUDED.confidence,
          last_event_at = EXCLUDED.last_event_at,
          updated_at = EXCLUDED.updated_at
        """,
        userId,
        kcCode,
        mastery,
        confidence,
        timestamp(occurredAt),
        timestamp(occurredAt));
  }

  private void upsertReviewState(
      Long userId, String kcCode, double mastery, int reps, int lapses, OffsetDateTime reviewedAt) {
    double stability = Math.max(0.5, Math.min(30.0, 1.0 + mastery * 18.0 + reps * 0.35));
    double difficulty = Math.max(0.05, Math.min(0.95, 1.0 - mastery + lapses * 0.02));
    OffsetDateTime dueAt = reviewedAt.plusHours(Math.round(stability * 24));
    jdbc.update(
        """
        INSERT INTO kc_review_state (
          user_id, kc_code, stability, difficulty, reps, lapses, last_review_at, due_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (user_id, kc_code) DO UPDATE SET
          stability = EXCLUDED.stability,
          difficulty = EXCLUDED.difficulty,
          reps = EXCLUDED.reps,
          lapses = EXCLUDED.lapses,
          last_review_at = EXCLUDED.last_review_at,
          due_at = EXCLUDED.due_at,
          updated_at = EXCLUDED.updated_at
        """,
        userId,
        kcCode,
        stability,
        difficulty,
        reps,
        lapses,
        timestamp(reviewedAt),
        timestamp(dueAt),
        timestamp(reviewedAt));
  }

  private LongTermEffectMetrics aggregate(String runId, Long classroomId, String cohort) {
    Integer students =
        jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM classroom_members cm
            JOIN users u ON u.id = cm.student_id
            WHERE cm.classroom_id = ? AND cm.status = 'ACTIVE' AND u.username LIKE ?
            """,
            Integer.class,
            classroomId,
            cohort.substring(0, 1) + "-" + runId + "-%");
    Double avgMastery =
        jdbc.queryForObject(
            """
            SELECT COALESCE(AVG(m.mastery_score), 0)
            FROM classroom_members cm
            JOIN users u ON u.id = cm.student_id
            JOIN kc_mastery m ON m.user_id = cm.student_id
            WHERE cm.classroom_id = ? AND cm.status = 'ACTIVE' AND u.username LIKE ?
            """,
            Double.class,
            classroomId,
            cohort.substring(0, 1) + "-" + runId + "-%");
    Integer scheduled =
        eventCount(runId, cohort, "review_scheduled");
    Integer completed =
        eventCount(runId, cohort, "review_completed");
    Integer generated =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM learning_resource
            WHERE content->>'simRun' = ? AND content->>'cohort' = ?
            """,
            Integer.class,
            runId,
            cohort);
    Integer consumed =
        jdbc.queryForObject(
            """
            SELECT COUNT(DISTINCT (payload->>'resourceId')::bigint)
            FROM learning_event_log
            WHERE source = ?
              AND event_type = 'resource_consumed'
              AND payload->>'simRun' = ?
              AND payload->>'cohort' = ?
            """,
            Integer.class,
            SOURCE,
            runId,
            cohort);
    return new LongTermEffectMetrics(
        students == null ? 0 : students,
        round(avgMastery == null ? 0.0 : avgMastery),
        ratio(completed, scheduled),
        ratio(consumed, generated),
        scheduled == null ? 0 : scheduled,
        completed == null ? 0 : completed,
        generated == null ? 0 : generated,
        consumed == null ? 0 : consumed);
  }

  private LongTermRawLogCounts rawLogCounts(String runId) {
    Integer mastery =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM kc_mastery_event WHERE reason LIKE ?",
            Integer.class,
            "long-term-sim:" + runId + ":%");
    Integer review =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM learning_event_log
            WHERE source = ? AND payload->>'simRun' = ?
              AND event_type IN ('review_scheduled', 'review_completed')
            """,
            Integer.class,
            SOURCE,
            runId);
    Integer resource =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM learning_event_log
            WHERE source = ? AND payload->>'simRun' = ? AND event_type = 'resource_consumed'
            """,
            Integer.class,
            SOURCE,
            runId);
    return new LongTermRawLogCounts(
        mastery == null ? 0 : mastery, review == null ? 0 : review, resource == null ? 0 : resource);
  }

  private Integer eventCount(String runId, String cohort, String eventType) {
    return jdbc.queryForObject(
        """
        SELECT COUNT(*) FROM learning_event_log
        WHERE source = ? AND event_type = ? AND payload->>'simRun' = ? AND payload->>'cohort' = ?
        """,
        Integer.class,
        SOURCE,
        eventType,
        runId,
        cohort);
  }

  private boolean shouldConsumeResource(String cohort, int studentIndex, int week, int resourceIndex) {
    if ("treatment".equals(cohort)) {
      return true;
    }
    return resourceIndex == 0 || (studentIndex + week) % 4 == 0;
  }

  private boolean reviewCompleted(String cohort, int studentIndex, int week, int kcIndex) {
    if ("treatment".equals(cohort)) {
      return (studentIndex + week + kcIndex) % 5 != 0;
    }
    return (studentIndex + week + kcIndex) % 3 == 0;
  }

  private double masteryDelta(String cohort, boolean completed) {
    if ("treatment".equals(cohort)) {
      return completed ? 0.055 : 0.015;
    }
    return completed ? 0.032 : 0.004;
  }

  private static double ratio(Integer numerator, Integer denominator) {
    if (denominator == null || denominator == 0 || numerator == null) {
      return 0.0;
    }
    return round(numerator.doubleValue() / denominator.doubleValue());
  }

  private static double clamp(double value) {
    return Math.max(0.0, Math.min(1.0, value));
  }

  private static double round(double value) {
    return Math.round(value * 1000.0) / 1000.0;
  }

  private static Timestamp timestamp(OffsetDateTime time) {
    return Timestamp.from(time.toInstant());
  }

  private static String uniqueJoinCode(String runId) {
    String suffix = Integer.toUnsignedString(runId.hashCode(), 36).toUpperCase();
    return ("LT" + suffix + "XXXX").substring(0, 6);
  }
}

record LongTermSimulationReport(
    Long classroomId,
    LongTermEffectMetrics baseline,
    LongTermEffectMetrics treatment,
    LongTermRawLogCounts rawLogCounts) {}

record LongTermEffectMetrics(
    int studentCount,
    double avgMastery,
    double reviewCompletionRate,
    double resourceUsageRate,
    int scheduledReviews,
    int completedReviews,
    int generatedResources,
    int consumedResources) {

  boolean winsAllMetricsAgainst(LongTermEffectMetrics baseline) {
    return avgMastery > baseline.avgMastery
        && reviewCompletionRate > baseline.reviewCompletionRate
        && resourceUsageRate > baseline.resourceUsageRate;
  }
}

record LongTermRawLogCounts(int masteryEvents, int reviewEvents, int resourceEvents) {}
