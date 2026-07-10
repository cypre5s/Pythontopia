-- Purpose: make Boss/Endless problem preloading observable and retryable.
--   * add PENDING / FAILED states instead of writing only successful READY rows;
--   * keep retry attempts and a bounded failure reason for diagnosis;
--   * include difficulty in the active-preload identity so an easy cached problem can never be
--     relabelled as a harder adaptive problem when it is consumed.
-- Tables affected: pregenerated_problem.
-- Irreversible risk: payload becomes nullable while a row is PENDING/FAILED. Rolling back code
--   without restoring those rows first would violate the old NOT NULL contract.

ALTER TABLE pregenerated_problem
  ALTER COLUMN payload DROP NOT NULL;

ALTER TABLE pregenerated_problem
  DROP CONSTRAINT IF EXISTS pregenerated_problem_status_check;

ALTER TABLE pregenerated_problem
  ADD CONSTRAINT pregenerated_problem_status_check
  CHECK (status IN ('PENDING', 'READY', 'FAILED', 'CONSUMED'));

ALTER TABLE pregenerated_problem
  ADD COLUMN IF NOT EXISTS attempt_count INT NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS error_message VARCHAR(500),
  ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- Old code could leave more than one READY row for the same dimensions. Retain the newest and
-- make superseded rows explicit failures before adding the active-job uniqueness invariant.
WITH ranked AS (
  SELECT id,
         row_number() OVER (
           PARTITION BY user_id, chapter, kc_code, kind, arena_level, difficulty
           ORDER BY created_at DESC, id DESC
         ) AS rn
  FROM pregenerated_problem
  WHERE status = 'READY'
)
UPDATE pregenerated_problem p
SET status = 'FAILED',
    error_message = 'superseded duplicate preload',
    updated_at = now()
FROM ranked r
WHERE p.id = r.id AND r.rn > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uq_pregenerated_problem_active_dims
  ON pregenerated_problem (
    user_id,
    chapter,
    COALESCE(kc_code, ''),
    COALESCE(kind, ''),
    COALESCE(arena_level, ''),
    COALESCE(difficulty, -1)
  )
  NULLS NOT DISTINCT
  WHERE status IN ('PENDING', 'READY');

CREATE INDEX IF NOT EXISTS idx_pregenerated_problem_prefetch_status
  ON pregenerated_problem (
    user_id,
    chapter,
    kc_code,
    kind,
    arena_level,
    difficulty,
    status,
    updated_at DESC
  );
