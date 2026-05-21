-- Purpose: persist actionable quests derived from learning_path, classroom assignments and system defaults.
-- Tables affected: quest, quest_progress.
-- Irreversible risk: dropping quest loses today's actions and history; quest_progress dropping
--                    loses the audit trail required for the review hall (stage 13).

CREATE TABLE IF NOT EXISTS quest (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  source VARCHAR(24) NOT NULL CHECK (
    source IN ('PATH_PLANNER', 'CLASSROOM', 'SYSTEM_DEFAULT')
  ),
  source_ref_id BIGINT,
  title VARCHAR(256) NOT NULL,
  description VARCHAR(1024),
  kc_code VARCHAR(64),
  node_type VARCHAR(24) NOT NULL CHECK (
    node_type IN ('READ_RESOURCE', 'SOLVE_PROBLEM', 'FIX_BUG', 'PLANT_KC', 'PROFILE_UPDATE')
  ),
  payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  due_date DATE NOT NULL,
  priority INT NOT NULL DEFAULT 5 CHECK (priority BETWEEN 0 AND 9),
  status VARCHAR(16) NOT NULL DEFAULT 'OPEN' CHECK (
    status IN ('OPEN', 'IN_PROGRESS', 'COMPLETED', 'SKIPPED', 'EXPIRED')
  ),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_quest_user_status_due
  ON quest(user_id, status, due_date);
CREATE INDEX IF NOT EXISTS idx_quest_user_source
  ON quest(user_id, source);
CREATE INDEX IF NOT EXISTS idx_quest_source_ref
  ON quest(source, source_ref_id);

CREATE TABLE IF NOT EXISTS quest_progress (
  id BIGSERIAL PRIMARY KEY,
  quest_id BIGINT NOT NULL REFERENCES quest(id) ON DELETE CASCADE,
  event_type VARCHAR(16) NOT NULL CHECK (
    event_type IN ('STARTED', 'STEP_DONE', 'COMPLETED', 'SKIPPED', 'EXPIRED')
  ),
  note VARCHAR(512),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_quest_progress_quest_created
  ON quest_progress(quest_id, created_at DESC);
