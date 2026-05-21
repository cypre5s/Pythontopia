-- Purpose: extend classroom_quest with optional anchors (problem id / resource type) and the
--          required idempotency key for batch-generated student quests.
-- Tables affected: classroom_quest.
-- Irreversible risk: dropping any of these columns destroys the audit anchor for the quests
--                    that were generated for students.

ALTER TABLE classroom_quest
  ADD COLUMN IF NOT EXISTS target_problem_id BIGINT
    REFERENCES course_problem(id) ON DELETE SET NULL;

ALTER TABLE classroom_quest
  ADD COLUMN IF NOT EXISTS target_resource_type VARCHAR(16);

ALTER TABLE classroom_quest
  ADD CONSTRAINT chk_classroom_quest_anchor
  CHECK (target_problem_id IS NOT NULL OR target_resource_type IS NOT NULL);

-- Idempotency key for batch quest generation: prevents duplicate student quests when the
-- teacher re-publishes the same classroom_quest row.
CREATE UNIQUE INDEX IF NOT EXISTS uniq_quest_classroom_user
  ON quest(user_id, source_ref_id)
  WHERE source = 'CLASSROOM';
