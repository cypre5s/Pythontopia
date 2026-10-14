-- Purpose: distinguish automatic Graph review from a human teacher decision and allow teachers
--   to override automatically approved questions without rewriting manual decision history.
-- Tables affected: boss_problem_review (adds review_source and updates its audit constraint).
-- Irreversible risk: dropping review_source removes whether an existing approved question was
--   admitted automatically or by a teacher; existing rows are reliably marked TEACHER because
--   V191 required a non-null user decider for every terminal decision.

ALTER TABLE boss_problem_review
  ADD COLUMN review_source VARCHAR(16) NOT NULL DEFAULT 'TEACHER'
    CHECK (review_source IN ('TEACHER', 'AUTOMATED'));

ALTER TABLE boss_problem_review
  DROP CONSTRAINT boss_problem_review_decision_audit;

ALTER TABLE boss_problem_review
  ADD CONSTRAINT boss_problem_review_decision_audit CHECK (
    (
      decision = 'PENDING'
      AND review_source = 'TEACHER'
      AND decider_id IS NULL
      AND decided_at IS NULL
      AND decision_reason IS NULL
      AND version = 1
    )
    OR
    (
      decision IN ('APPROVED', 'REJECTED')
      AND review_source = 'TEACHER'
      AND decider_id IS NOT NULL
      AND decided_at IS NOT NULL
      AND NULLIF(BTRIM(decision_reason), '') IS NOT NULL
      AND version >= 2
    )
    OR
    (
      decision = 'APPROVED'
      AND review_source = 'AUTOMATED'
      AND decider_id IS NULL
      AND decided_at IS NOT NULL
      AND NULLIF(BTRIM(decision_reason), '') IS NOT NULL
      AND version >= 2
    )
  );

COMMENT ON COLUMN boss_problem_review.review_source IS
  'TEACHER for human decisions; AUTOMATED only for candidates passing the Graph review pipeline.';
