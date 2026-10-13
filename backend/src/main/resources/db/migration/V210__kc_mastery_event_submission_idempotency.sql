-- Purpose: prevent a repeated server-verified submission event from changing KC mastery twice.
-- Tables affected: kc_mastery_event.
-- Irreversible risk: dropping the key/index removes deduplication identity, while existing event history remains intact.

ALTER TABLE kc_mastery_event
  ADD COLUMN idempotency_key VARCHAR(128);

CREATE UNIQUE INDEX uq_kc_mastery_event_user_idempotency_key
  ON kc_mastery_event (user_id, idempotency_key)
  WHERE idempotency_key IS NOT NULL;
