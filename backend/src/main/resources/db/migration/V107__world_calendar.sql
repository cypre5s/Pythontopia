-- Purpose: extend world_session with calendar/season fields, create world_event log, and
--          recreate the V100 game_bootstrap_view so the new calendar fields ship to the client
--          in one shot.
-- Tables affected: world_session (added columns), world_event (new), game_bootstrap_view (replaced).
-- Irreversible risk: adding NOT NULL columns with defaults is safe; dropping the new columns later
--                    loses calendar state and breaks the recreated view.

ALTER TABLE world_session
  ADD COLUMN IF NOT EXISTS month_index INTEGER NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS weekday VARCHAR(16) NOT NULL DEFAULT 'monday',
  ADD COLUMN IF NOT EXISTS season VARCHAR(16) NOT NULL DEFAULT 'spring',
  ADD COLUMN IF NOT EXISTS time_of_day VARCHAR(16) NOT NULL DEFAULT 'morning';

ALTER TABLE world_session
  DROP CONSTRAINT IF EXISTS world_session_month_check;
ALTER TABLE world_session
  ADD CONSTRAINT world_session_month_check CHECK (month_index BETWEEN 1 AND 12);

ALTER TABLE world_session
  DROP CONSTRAINT IF EXISTS world_session_season_check;
ALTER TABLE world_session
  ADD CONSTRAINT world_session_season_check
    CHECK (season IN ('spring', 'summer', 'autumn', 'winter'));

ALTER TABLE world_session
  DROP CONSTRAINT IF EXISTS world_session_weekday_check;
ALTER TABLE world_session
  ADD CONSTRAINT world_session_weekday_check
    CHECK (weekday IN ('monday','tuesday','wednesday','thursday','friday','saturday','sunday'));

ALTER TABLE world_session
  DROP CONSTRAINT IF EXISTS world_session_time_of_day_check;
ALTER TABLE world_session
  ADD CONSTRAINT world_session_time_of_day_check
    CHECK (time_of_day IN ('dawn','morning','noon','afternoon','dusk','evening','night'));

CREATE TABLE IF NOT EXISTS world_event (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  date_iso DATE NOT NULL,
  event_type VARCHAR(32) NOT NULL,
  payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  is_processed BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_world_event_user_date ON world_event(user_id, date_iso);
CREATE INDEX IF NOT EXISTS idx_world_event_unprocessed
  ON world_event(user_id, is_processed)
  WHERE is_processed = false;

COMMENT ON TABLE world_event IS '玩家世界事件队列（节日、天气、班级公告等），按日期触发';

-- Recreate the bootstrap view so the new calendar columns ship in the single GET payload.
-- 重新构造视图列以纳入 V107 新增的日历字段。
-- PostgreSQL 的 CREATE OR REPLACE VIEW 不允许改变现有列顺序/名称，所以先 DROP 再 CREATE。
DROP VIEW IF EXISTS game_bootstrap_view;
CREATE VIEW game_bootstrap_view AS
SELECT
  u.id                          AS user_id,
  u.username                    AS username,
  u.display_name                AS display_name,
  u.role                         AS role,
  ws.id                          AS world_session_id,
  ws.day_index                  AS day_index,
  ws.weather                    AS weather,
  ws.in_game_minutes            AS in_game_minutes,
  ws.month_index                AS month_index,
  ws.weekday                    AS weekday,
  ws.season                     AS season,
  ws.time_of_day                AS time_of_day,
  ws.started_at                 AS world_started_at,
  ws.last_active_at             AS world_last_active_at,
  ps.id                          AS player_state_id,
  ps.avatar_key                 AS player_avatar_key,
  ps.map_key                    AS player_map_key,
  ps.tile_x                     AS player_tile_x,
  ps.tile_y                     AS player_tile_y,
  ps.direction                  AS player_direction,
  ps.updated_at                 AS player_updated_at,
  lp.id                          AS learner_profile_id,
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
