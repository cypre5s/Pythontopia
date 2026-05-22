-- Purpose: aggregate 1:1 RPG bootstrap state (world_session + player_state + learner_profile)
--          into a single view so the frontend can hydrate the pixel town in one GET.
-- Tables affected: creates view game_bootstrap_view (read-only, no row changes).
-- Irreversible risk: none; view is a non-materialised projection that can be replaced.
-- Note: only 1:1 relations are aggregated here. crop_tile_state, quest, learning_path_node and
--       npc_dialog_state remain as separate Controller queries because they are 1:N.

CREATE OR REPLACE VIEW game_bootstrap_view AS
SELECT
  u.id                          AS user_id,
  u.username                    AS username,
  u.display_name                AS display_name,
  u.role                         AS role,
  ws.id                          AS world_session_id,
  ws.day_index                  AS day_index,
  ws.weather                    AS weather,
  ws.in_game_minutes            AS in_game_minutes,
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
