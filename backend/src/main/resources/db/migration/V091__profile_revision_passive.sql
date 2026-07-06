-- Purpose: link profile revisions to an evaluation snapshot and allow EVALUATION/DEBUG_FIX
--          / PRACTICE_PASS / PRACTICE_FAIL passive sources.
-- Tables affected: profile_revision.
-- Irreversible risk: extending the CHECK constraint is reversible by recreating the column;
--                    dropping evaluation_id requires re-running the migration.

ALTER TABLE profile_revision
  ADD COLUMN IF NOT EXISTS evaluation_id BIGINT
    REFERENCES learner_evaluation(id) ON DELETE SET NULL;

ALTER TABLE profile_revision DROP CONSTRAINT IF EXISTS profile_revision_source_check;
ALTER TABLE profile_revision ADD CONSTRAINT profile_revision_source_check
  CHECK (source IN (
    'INTERVIEW',
    'SUBMISSION',
    'DEBUG',
    'RESOURCE_FEEDBACK',
    'QUEST',
    'CLASSROOM',
    'EVALUATION',
    'PRACTICE_PASS',
    'PRACTICE_FAIL',
    'DEBUG_FIX'
  ));
