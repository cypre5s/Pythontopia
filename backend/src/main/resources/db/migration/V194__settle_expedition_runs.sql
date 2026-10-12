-- Purpose: prevent client snapshots from replacing an active expedition id and mark a run settled atomically.
-- Tables affected: expedition_run.
-- Irreversible risk: none; existing ACTIVE rows remain resumable and may settle through the score endpoint.

ALTER TABLE expedition_run
  DROP CONSTRAINT expedition_run_status_check,
  ADD CONSTRAINT expedition_run_status_check CHECK (status IN ('ACTIVE', 'SETTLED'));
