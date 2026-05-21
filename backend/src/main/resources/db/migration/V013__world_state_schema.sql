-- Purpose: create compact town world state tables for the learner's pixel town.
-- Tables affected: world_session, player_state, npc_state, weather_state, crop_tile_state.
-- Irreversible risk: none; this migration only creates tables and indexes.
-- Alethicode source: none; Pythontopia world state is a new project-specific schema.

CREATE TABLE IF NOT EXISTS world_session (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
  day_index INTEGER NOT NULL,
  weather VARCHAR(16) NOT NULL,
  in_game_minutes INTEGER NOT NULL,
  started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_active_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS player_state (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
  avatar_key VARCHAR(64) NOT NULL DEFAULT 'student-default',
  map_key VARCHAR(64) NOT NULL DEFAULT 'town-beta',
  tile_x INTEGER NOT NULL DEFAULT 12,
  tile_y INTEGER NOT NULL DEFAULT 10,
  direction VARCHAR(8) NOT NULL DEFAULT 'DOWN',
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS npc_state (
  id BIGSERIAL PRIMARY KEY,
  world_session_id BIGINT NOT NULL REFERENCES world_session(id) ON DELETE CASCADE,
  npc_key VARCHAR(64) NOT NULL,
  mood VARCHAR(32) NOT NULL DEFAULT 'NEUTRAL',
  last_talk_at TIMESTAMPTZ,
  UNIQUE (world_session_id, npc_key)
);

CREATE TABLE IF NOT EXISTS weather_state (
  id BIGSERIAL PRIMARY KEY,
  world_session_id BIGINT NOT NULL UNIQUE REFERENCES world_session(id) ON DELETE CASCADE,
  current_weather VARCHAR(16) NOT NULL,
  forecast JSONB NOT NULL DEFAULT '[]'::jsonb
);

CREATE TABLE IF NOT EXISTS crop_tile_state (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  tile_x INTEGER NOT NULL,
  tile_y INTEGER NOT NULL,
  kc_code VARCHAR(64) NOT NULL,
  growth_stage INTEGER NOT NULL DEFAULT 0,
  mastery_score NUMERIC(5, 4) NOT NULL DEFAULT 0,
  last_watered_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (user_id, tile_x, tile_y)
);

CREATE INDEX IF NOT EXISTS idx_crop_tile_user_kc ON crop_tile_state(user_id, kc_code);
