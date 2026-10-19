-- Purpose: record the latest admin repairing a profile projection and index failed-row lookup.
-- Tables affected: learner_profile_projection_outbox.
-- Irreversible risk: dropping the new columns loses latest replay attribution.

ALTER TABLE learner_profile_projection_outbox
  ADD COLUMN last_replayed_by BIGINT,
  ADD COLUMN last_replayed_at TIMESTAMPTZ;

CREATE INDEX idx_profile_projection_outbox_failed
  ON learner_profile_projection_outbox (failed_at, id)
  WHERE status = 'FAILED';

COMMENT ON COLUMN learner_profile_projection_outbox.last_replayed_by
  IS 'User ID of the admin who most recently repaired and replayed this projection.';

COMMENT ON COLUMN learner_profile_projection_outbox.last_replayed_at
  IS 'When the most recent manual repair and replay was accepted.';
