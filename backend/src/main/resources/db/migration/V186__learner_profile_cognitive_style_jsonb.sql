-- Purpose: widen learner_profile.cognitive_style from VARCHAR(64) to JSONB so ProfileAgent output
--   can always be stored.
--   V012 declared the column VARCHAR(64), but ProfileRepository has always written it as `?::jsonb`
--   with a CognitiveStyleDto payload and read it back through Jackson. The serialised shape
--   {"fieldDependent":<double>,"sequentialVsHolistic":<double>} already spends 42 characters on
--   fixed overhead, leaving ~22 characters for two doubles. A high-precision value such as
--   0.3333333333333333 (Python/LLM float repr) pushes the payload past 64 characters and the INSERT
--   fails with `value too long for type character varying(64)`, rolling back the whole interview
--   turn and surfacing a 500 to the student.
-- Tables affected: learner_profile (column type change); game_bootstrap_view is dropped and
--   recreated verbatim because PostgreSQL refuses ALTER TYPE on a column a view depends on
--   (same DROP/recreate dance as V113).
-- Irreversible risk: going back to VARCHAR(64) would truncate/reject any row whose JSON exceeds 64
--   characters, i.e. exactly the rows this migration exists to allow. Legacy non-JSON text values,
--   if any exist, would abort the USING cast; the CASE guard below rewrites them into
--   {"raw": <text>} instead of failing the deploy.

DROP VIEW IF EXISTS game_bootstrap_view;

ALTER TABLE learner_profile
  ALTER COLUMN cognitive_style TYPE JSONB
  USING (
    CASE
      WHEN cognitive_style IS NULL OR btrim(cognitive_style) = '' THEN NULL
      WHEN btrim(cognitive_style) LIKE '{%' OR btrim(cognitive_style) LIKE '[%'
        THEN cognitive_style::jsonb
      ELSE jsonb_build_object('raw', cognitive_style)
    END
  );

COMMENT ON COLUMN learner_profile.cognitive_style IS
  'ProfileAgent 访谈产出的认知风格结构体（CognitiveStyleDto: field_dependent / '
  'sequential_vs_holistic）。V186 之前为 VARCHAR(64)，高精度小数会撑爆列宽导致整场访谈回滚。';

-- 重建 V113 的视图（字段集与列顺序保持一致）；cognitive_style 现在以 jsonb 形式透出。
CREATE VIEW game_bootstrap_view AS
SELECT
  u.id                          AS user_id,
  u.username                    AS username,
  u.display_name                AS display_name,
  u.role                        AS role,
  ws.id                         AS world_session_id,
  ws.day_index                  AS day_index,
  ws.weather                    AS weather,
  ws.in_game_minutes            AS in_game_minutes,
  ws.month_index                AS month_index,
  ws.weekday                    AS weekday,
  ws.season                     AS season,
  ws.time_of_day                AS time_of_day,
  ws.started_at                 AS world_started_at,
  ws.last_active_at             AS world_last_active_at,
  ps.id                         AS player_state_id,
  ps.avatar_key                 AS player_avatar_key,
  ps.map_key                    AS player_map_key,
  ps.tile_x                     AS player_tile_x,
  ps.tile_y                     AS player_tile_y,
  ps.direction                  AS player_direction,
  ps.updated_at                 AS player_updated_at,
  lp.id                         AS learner_profile_id,
  lp.pack_id                    AS learner_pack_id,
  lp.major                      AS learner_major,
  lp.base_level                 AS learner_base_level,
  lp.learning_goal              AS learner_learning_goal,
  lp.cognitive_style            AS learner_cognitive_style,
  lp.learning_preference        AS learner_learning_preference,
  lp.error_patterns             AS learner_error_patterns,
  lp.practice_capability        AS learner_practice_capability,
  lp.emotion_persistence        AS learner_emotion_persistence,
  lp.narrative_summary          AS learner_narrative_summary,
  lp.updated_at                 AS learner_profile_updated_at
FROM users u
LEFT JOIN world_session ws    ON ws.user_id = u.id
LEFT JOIN player_state ps     ON ps.user_id = u.id
LEFT JOIN learner_profile lp  ON lp.user_id = u.id;
