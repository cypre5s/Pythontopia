-- Purpose: persist KC variant handoff and profile-driven depth target for Bug Mine mastery confirmation.
-- Tables affected: bug_mine_session.
-- Irreversible risk: dropping variant columns loses L5 migration audit trail.

ALTER TABLE bug_mine_session
  ADD COLUMN IF NOT EXISTS variant_problem_id BIGINT REFERENCES course_problem(id) ON DELETE SET NULL;

ALTER TABLE bug_mine_session
  ADD COLUMN IF NOT EXISTS variant_submission_id BIGINT REFERENCES practice_submission(id) ON DELETE SET NULL;

ALTER TABLE bug_mine_session
  ADD COLUMN IF NOT EXISTS mastery_confirmed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE bug_mine_session
  ADD COLUMN IF NOT EXISTS profile_depth_target INT NOT NULL DEFAULT 3;

ALTER TABLE bug_mine_session
  DROP CONSTRAINT IF EXISTS chk_bug_mine_session_profile_depth_target;

ALTER TABLE bug_mine_session
  ADD CONSTRAINT chk_bug_mine_session_profile_depth_target
  CHECK (profile_depth_target BETWEEN 1 AND 5);

CREATE INDEX IF NOT EXISTS idx_bug_mine_session_variant_problem
  ON bug_mine_session(variant_problem_id)
  WHERE variant_problem_id IS NOT NULL;
