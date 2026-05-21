-- Purpose: extend resource_run with correlation id, broader scenarios and graph trace.
-- Tables affected: resource_run.
-- Irreversible risk: dropping these columns loses cross-run correlation and graph traces.

ALTER TABLE resource_run
  ADD COLUMN IF NOT EXISTS correlation_id UUID,
  ADD COLUMN IF NOT EXISTS graph_trace JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE resource_run
  DROP CONSTRAINT IF EXISTS resource_run_status_check;

ALTER TABLE resource_run
  ADD CONSTRAINT resource_run_status_check
    CHECK (status IN ('SUCCESS', 'PARTIAL', 'FAILED'));

CREATE INDEX IF NOT EXISTS idx_resource_run_correlation
  ON resource_run(correlation_id);
CREATE INDEX IF NOT EXISTS idx_resource_run_user_scenario
  ON resource_run(user_id, scenario);
