-- Purpose: quarantine exhausted profile projection retries and support manual repair/replay.
-- Tables affected: learner_profile_projection_outbox.
-- Irreversible risk: downgrade loses failed timestamps and operator replay counts.
-- FAILED rows must be reconciled before restoring the old status check.

ALTER TABLE learner_profile_projection_outbox
  DROP CONSTRAINT learner_profile_projection_outbox_status_check;

ALTER TABLE learner_profile_projection_outbox
  ADD CONSTRAINT learner_profile_projection_outbox_status_check
    CHECK (status IN ('PENDING', 'APPLIED', 'FAILED')),
  ADD COLUMN failed_at TIMESTAMPTZ,
  ADD COLUMN manual_replay_count INTEGER NOT NULL DEFAULT 0
    CHECK (manual_replay_count >= 0);

DROP INDEX idx_profile_projection_outbox_user_order;
CREATE INDEX idx_profile_projection_outbox_user_order
  ON learner_profile_projection_outbox (user_id, id)
  WHERE status IN ('PENDING', 'FAILED');

COMMENT ON COLUMN learner_profile_projection_outbox.failed_at
  IS 'When automatic retries were exhausted; unresolved failed rows continue blocking later updates for the same user.';

COMMENT ON COLUMN learner_profile_projection_outbox.manual_replay_count
  IS 'Number of validated operator repairs/replays started for this projection.';
