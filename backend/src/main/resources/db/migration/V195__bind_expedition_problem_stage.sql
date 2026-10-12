-- Purpose: bind every pre-generated challenge to its requested boss stage and expedition run.
-- Tables affected: pregenerated_problem.
-- Irreversible risk: rollback removes the stage/run identity used to reject cross-stage and
--   cross-run problem reuse; READY legacy rows are backfilled from their stored payload when possible.

ALTER TABLE pregenerated_problem
  ADD COLUMN stage INT;

UPDATE pregenerated_problem
SET stage = CASE
  WHEN payload->>'stage' ~ '^[1-4]$' THEN (payload->>'stage')::INT
  ELSE 1
END;

ALTER TABLE pregenerated_problem
  ALTER COLUMN stage SET DEFAULT 1,
  ALTER COLUMN stage SET NOT NULL,
  ADD CONSTRAINT pregenerated_problem_stage_range CHECK (stage BETWEEN 1 AND 4);

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
    COALESCE(expedition_run_id, '00000000-0000-0000-0000-000000000000'::UUID)
  )
  NULLS NOT DISTINCT
  WHERE status IN ('PENDING', 'READY');

CREATE INDEX idx_pregenerated_problem_run_stage
  ON pregenerated_problem (user_id, expedition_run_id, chapter, stage, status, created_at DESC);
