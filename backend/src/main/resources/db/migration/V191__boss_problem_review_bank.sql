-- Purpose: replace the browser-only teacher review queue with a server-authoritative, audited
--   problem review bank for course pack 43. Full BossInternalProblem payloads (including hidden
--   cases and reference solutions) remain in backend storage; only approved projections can be
--   served to students.
-- Tables affected: boss_problem_review (new); foreign keys to course_pack and users.
-- Irreversible risk: dropping this table loses pending review work and approval audit history.
--   The migration does not modify existing Boss/pregenerated_problem rows and is forward-only.

CREATE TABLE boss_problem_review (
  id BIGSERIAL PRIMARY KEY,
  pack_id BIGINT NOT NULL DEFAULT 43 REFERENCES course_pack(id) ON DELETE RESTRICT,
  source_problem_id VARCHAR(256) NOT NULL,
  chapter INT NOT NULL CHECK (chapter BETWEEN 1 AND 7),
  stage INT NOT NULL CHECK (stage BETWEEN 1 AND 4),
  kc_code VARCHAR(64) NOT NULL,
  kind VARCHAR(32),
  difficulty INT NOT NULL CHECK (difficulty BETWEEN 1 AND 5),
  payload JSONB NOT NULL CHECK (jsonb_typeof(payload) = 'object'),
  decision VARCHAR(16) NOT NULL DEFAULT 'PENDING'
    CHECK (decision IN ('PENDING', 'APPROVED', 'REJECTED')),
  requester_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  decider_id BIGINT REFERENCES users(id) ON DELETE RESTRICT,
  decided_at TIMESTAMPTZ,
  decision_reason VARCHAR(1000),
  version INT NOT NULL DEFAULT 1 CHECK (version > 0),
  CONSTRAINT boss_problem_review_pack_43 CHECK (pack_id = 43),
  CONSTRAINT boss_problem_review_decision_audit CHECK (
    (
      decision = 'PENDING'
      AND decider_id IS NULL
      AND decided_at IS NULL
      AND decision_reason IS NULL
      AND version = 1
    )
    OR
    (
      decision IN ('APPROVED', 'REJECTED')
      AND decider_id IS NOT NULL
      AND decided_at IS NOT NULL
      AND NULLIF(BTRIM(decision_reason), '') IS NOT NULL
      AND version >= 2
    )
  )
);

CREATE INDEX idx_boss_problem_review_queue
  ON boss_problem_review (decision, requested_at DESC, id DESC);

CREATE INDEX idx_boss_problem_review_approved_lookup
  ON boss_problem_review (
    pack_id,
    chapter,
    stage,
    kc_code,
    kind,
    difficulty,
    decided_at DESC,
    id DESC
  )
  WHERE decision = 'APPROVED';

COMMENT ON TABLE boss_problem_review IS
  'Pack 43 global Boss problem review queue and approved bank; payload is backend-only.';
COMMENT ON COLUMN boss_problem_review.payload IS
  'Complete BossInternalProblem JSON including hidden test cases and reference solution; never expose directly.';
