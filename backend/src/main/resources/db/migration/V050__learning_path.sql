-- Purpose: persist personalised 7-day learning paths produced by the PathPlannerAgent.
-- Tables affected: learning_path, learning_path_node.
-- Irreversible risk: dropping these tables loses every historical plan and forces re-planning;
--                    archived plans are referenced from /docs/learner_path.md for audit.

CREATE TABLE IF NOT EXISTS learning_path (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  pack_id BIGINT NOT NULL REFERENCES course_pack(id) ON DELETE CASCADE,
  version INT NOT NULL CHECK (version >= 1),
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
    CHECK (status IN ('ACTIVE', 'ARCHIVED')),
  goal_summary VARCHAR(512),
  generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  next_review_at TIMESTAMPTZ,
  source_run_id BIGINT REFERENCES resource_run(id) ON DELETE SET NULL,
  UNIQUE (user_id, pack_id, version)
);

CREATE INDEX IF NOT EXISTS idx_learning_path_user_status
  ON learning_path(user_id, status);
CREATE INDEX IF NOT EXISTS idx_learning_path_user_generated
  ON learning_path(user_id, generated_at DESC);

CREATE TABLE IF NOT EXISTS learning_path_node (
  id BIGSERIAL PRIMARY KEY,
  path_id BIGINT NOT NULL REFERENCES learning_path(id) ON DELETE CASCADE,
  day_index INT NOT NULL CHECK (day_index BETWEEN 1 AND 7),
  order_in_day INT NOT NULL CHECK (order_in_day >= 1),
  kc_code VARCHAR(64) NOT NULL,
  node_type VARCHAR(24) NOT NULL CHECK (
    node_type IN ('READ_RESOURCE', 'SOLVE_PROBLEM', 'FIX_BUG', 'PLANT_KC', 'PROFILE_UPDATE')
  ),
  reason_summary VARCHAR(512),
  evidence_summary VARCHAR(512),
  suggested_resource_type VARCHAR(16),
  suggested_problem_id BIGINT,
  payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  UNIQUE (path_id, day_index, order_in_day)
);

CREATE INDEX IF NOT EXISTS idx_learning_path_node_path_day
  ON learning_path_node(path_id, day_index, order_in_day);
CREATE INDEX IF NOT EXISTS idx_learning_path_node_kc
  ON learning_path_node(kc_code);
