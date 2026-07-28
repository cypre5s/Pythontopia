package com.pythontopia.ai.runtime;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AiRuntimePlatformSnapshotRepository {

  private static final String SNAPSHOT_SQL =
      """
      WITH active_students AS (
        SELECT id
        FROM users
        WHERE role = 'STUDENT' AND status = 'ACTIVE'
      ),
      evidence_users AS (
        SELECT user_id
        FROM learning_event_log
        WHERE source IN ('backend', 'graph')
        UNION
        SELECT user_id FROM learning_resource
        UNION
        SELECT user_id FROM resource_learning_check
        UNION
        SELECT user_id FROM kc_mastery_event
      ),
      touched_kcs AS (
        SELECT NULLIF(le.payload->>'kcCode', '') AS kc_code
        FROM learning_event_log le
        JOIN active_students student ON student.id = le.user_id
        WHERE le.source IN ('backend', 'graph')
        UNION
        SELECT jsonb_array_elements_text(
          CASE
            WHEN jsonb_typeof(le.payload->'kcCodes') = 'array' THEN le.payload->'kcCodes'
            ELSE '[]'::jsonb
          END
        ) AS kc_code
        FROM learning_event_log le
        JOIN active_students student ON student.id = le.user_id
        WHERE le.source IN ('backend', 'graph')
        UNION
        SELECT unnest(lr.kc_codes) AS kc_code
        FROM learning_resource lr
        JOIN active_students student ON student.id = lr.user_id
        UNION
        SELECT check_result.kc_code
        FROM resource_learning_check check_result
        JOIN active_students student ON student.id = check_result.user_id
        UNION
        SELECT mastery_event.kc_code
        FROM kc_mastery_event mastery_event
        JOIN active_students student ON student.id = mastery_event.user_id
      )
      SELECT
        (SELECT COUNT(*) FROM active_students) AS active_student_count,
        (
          SELECT COUNT(*)
          FROM evidence_users evidence_user
          JOIN active_students student ON student.id = evidence_user.user_id
        ) AS evidence_learner_count,
        (
          SELECT COUNT(*)
          FROM learning_event_log event_log
          JOIN active_students student ON student.id = event_log.user_id
          WHERE event_log.event_type = 'practice_failed'
            AND event_log.source IN ('backend', 'graph')
        ) AS failed_practice_count,
        (
          SELECT COUNT(*)
          FROM resource_run run
          JOIN active_students student ON student.id = run.user_id
        ) AS resource_run_count,
        (
          SELECT COUNT(*)
          FROM touched_kcs
          WHERE kc_code IS NOT NULL AND BTRIM(kc_code) <> ''
        ) AS touched_kc_count
      """;

  private final JdbcTemplate jdbc;

  public AiRuntimePlatformSnapshotRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public PlatformSnapshot snapshot() {
    PlatformSnapshot snapshot =
        jdbc.queryForObject(
            SNAPSHOT_SQL,
            (rs, rowNum) ->
                new PlatformSnapshot(
                    rs.getInt("active_student_count"),
                    rs.getInt("evidence_learner_count"),
                    rs.getInt("failed_practice_count"),
                    rs.getInt("resource_run_count"),
                    rs.getInt("touched_kc_count")));
    return snapshot == null ? new PlatformSnapshot(0, 0, 0, 0, 0) : snapshot;
  }

  public record PlatformSnapshot(
      int activeStudentCount,
      int evidenceLearnerCount,
      int failedPracticeCount,
      int resourceRunCount,
      int touchedKcCount) {}
}
