-- Purpose: record agent resource generation and profile interview runs.
-- Tables affected: resource_run.
-- Irreversible risk: stored run audit data would be lost if dropped.

CREATE TABLE IF NOT EXISTS resource_run (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  agent_name VARCHAR(64) NOT NULL,
  scenario VARCHAR(64) NOT NULL,
  input_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
  output_artifact JSONB NOT NULL DEFAULT '{}'::jsonb,
  evidence_page_ids BIGINT[] NOT NULL DEFAULT ARRAY[]::BIGINT[],
  safety_result JSONB NOT NULL DEFAULT '{}'::jsonb,
  grounding_result JSONB NOT NULL DEFAULT '{}'::jsonb,
  status VARCHAR(16) NOT NULL CHECK (status IN ('SUCCESS', 'PARTIAL', 'FAILED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_resource_run_user_created ON resource_run(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_resource_run_agent_status ON resource_run(agent_name, status);
