-- R-12 允许 player_state.direction 接受 8 方向（UP_RIGHT 等），原 VARCHAR(8) 容不下 DOWN_RIGHT(10字)。
-- 必须先 DROP 依赖此列的视图，ALTER 后重建。

DROP VIEW IF EXISTS game_bootstrap_view;

ALTER TABLE player_state ALTER COLUMN direction TYPE VARCHAR(16);
COMMENT ON COLUMN player_state.direction IS 'UP/DOWN/LEFT/RIGHT/UP_LEFT/UP_RIGHT/DOWN_LEFT/DOWN_RIGHT';

-- 重建 V107 创建的视图（保持字段集与列顺序与 V107 一致）。
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
