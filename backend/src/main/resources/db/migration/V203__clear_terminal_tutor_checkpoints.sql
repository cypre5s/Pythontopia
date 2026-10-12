-- Purpose: remove sensitive AutoGen state after a Tutor run has reached a terminal outcome.
-- Affected table: tutor_workflow_autogen_checkpoint; no schema changes.
-- Irreversible risk: completed and failed checkpoint payloads are discarded; session cards and projections remain.

UPDATE tutor_workflow_autogen_checkpoint
SET checkpoint = '{}'::jsonb
WHERE status IN ('COMPLETED', 'FAILED')
  AND checkpoint <> '{}'::jsonb;
