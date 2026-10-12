-- Purpose: bind every expedition-issued Boss problem to one map node and persist server-owned
--          challenge settlement so retries and duplicate question IDs cannot mutate another node.
-- Tables affected: pregenerated_problem, expedition_challenge_settlement.
-- Irreversible risk: legacy active expedition prefetch rows without a node binding are failed and
--          must be generated again; dropping the settlement table loses the gameplay audit trail.

ALTER TABLE pregenerated_problem
  ADD COLUMN expedition_node_id VARCHAR(32),
  ADD COLUMN question_fingerprint VARCHAR(64);

-- Existing run-scoped jobs cannot be safely assigned to a node after the fact.
UPDATE pregenerated_problem
SET status = 'FAILED',
    payload = NULL,
    problem_id = NULL,
    error_message = '远征题目缺少节点绑定，请重新生成',
    updated_at = now()
WHERE expedition_run_id IS NOT NULL
  AND expedition_node_id IS NULL
  AND status IN ('PENDING', 'READY');

DROP INDEX uq_pregenerated_problem_active_dims;

CREATE UNIQUE INDEX uq_pregenerated_problem_active_dims
  ON pregenerated_problem (
    user_id,
    chapter,
    stage,
    COALESCE(kc_code, ''),
    COALESCE(kind, ''),
    COALESCE(arena_level, ''),
    COALESCE(difficulty, -1),
    COALESCE(expedition_run_id, '00000000-0000-0000-0000-000000000000'::UUID),
    COALESCE(expedition_node_id, '')
  )
  NULLS NOT DISTINCT
  WHERE status IN ('PENDING', 'READY');

CREATE INDEX idx_pregenerated_problem_expedition_node
  ON pregenerated_problem (user_id, expedition_run_id, expedition_node_id, problem_id)
  WHERE expedition_run_id IS NOT NULL;

CREATE TABLE expedition_challenge_settlement (
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  run_id UUID NOT NULL,
  question_fingerprint VARCHAR(64) NOT NULL,
  problem_id VARCHAR(256) NOT NULL,
  node_id VARCHAR(32) NOT NULL,
  passed BOOLEAN NOT NULL,
  score INTEGER NOT NULL CHECK (score >= 0),
  first_aid_used BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, run_id, question_fingerprint)
);

CREATE INDEX idx_expedition_challenge_settlement_run
  ON expedition_challenge_settlement (user_id, run_id, created_at DESC);

COMMENT ON COLUMN pregenerated_problem.expedition_node_id
  IS 'Server map node that requested an expedition problem; never supplied during settlement as authority';
