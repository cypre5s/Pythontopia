-- Purpose: allow BUG_MINE_ECHO as a practice_submission source for forgetting-echo review sessions.
-- Tables affected: practice_submission.
-- Irreversible risk: removing enum value breaks historical forgetting-echo submissions.

ALTER TABLE practice_submission
  DROP CONSTRAINT IF EXISTS practice_submission_source_check;

ALTER TABLE practice_submission
  ADD CONSTRAINT practice_submission_source_check
  CHECK (source IN ('TRAINING', 'PATH_QUEST', 'BUG_MINE_RETRY', 'BUG_MINE_ECHO', 'BUG_MINE_VARIANT', 'CLASSROOM'));
