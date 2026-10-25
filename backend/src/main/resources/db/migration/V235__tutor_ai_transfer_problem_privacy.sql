-- Purpose: store AI Tutor transfer questions as owner-scoped, formally judgeable course problems.
-- Affected tables: adds private ownership/idempotency columns to course_problem; creates a high-range ID sequence.
-- Irreversible risk: deleting a private transfer problem cascades its practice submissions and judge results.

ALTER TABLE course_problem
  ADD COLUMN tutor_owner_user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
  ADD COLUMN tutor_source_problem_id BIGINT,
  ADD COLUMN tutor_session_id BIGINT,
  ADD COLUMN tutor_run_id UUID,
  ADD COLUMN tutor_request_hash VARCHAR(64),
  ADD CONSTRAINT ck_course_problem_tutor_transfer_binding CHECK (
    (tutor_owner_user_id IS NULL AND tutor_source_problem_id IS NULL
      AND tutor_session_id IS NULL AND tutor_run_id IS NULL AND tutor_request_hash IS NULL)
    OR
    (tutor_owner_user_id IS NOT NULL AND tutor_source_problem_id IS NOT NULL
      AND tutor_session_id IS NOT NULL AND tutor_run_id IS NOT NULL
      AND tutor_request_hash ~ '^[0-9a-f]{64}$')
  );

CREATE UNIQUE INDEX uq_course_problem_tutor_transfer_run
  ON course_problem (tutor_session_id, tutor_run_id)
  WHERE tutor_owner_user_id IS NOT NULL;

CREATE INDEX idx_course_problem_tutor_owner
  ON course_problem (tutor_owner_user_id, id)
  WHERE tutor_owner_user_id IS NOT NULL;

CREATE SEQUENCE tutor_transfer_problem_id_seq MINVALUE 1000000000 START WITH 1000000000;
SELECT setval(
  'tutor_transfer_problem_id_seq',
  GREATEST(1000000000, COALESCE((SELECT MAX(id) + 1 FROM course_problem), 1000000000)),
  false
);
