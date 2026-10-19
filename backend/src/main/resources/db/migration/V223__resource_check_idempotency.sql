-- Purpose: persist resource micro-check idempotency keys and exact replay responses.
-- Tables affected: resource_learning_check.
-- Irreversible risk: removing these columns/index discards replay data; retrying old requests can create duplicate checks and mastery events.

ALTER TABLE resource_learning_check
  ADD COLUMN idempotency_key VARCHAR(128),
  ADD COLUMN request_hash VARCHAR(64),
  ADD COLUMN response_payload JSONB,
  ADD CONSTRAINT resource_learning_check_idempotency_pair_check
    CHECK ((idempotency_key IS NULL) = (request_hash IS NULL)),
  ADD CONSTRAINT resource_learning_check_idempotency_hash_check
    CHECK (request_hash IS NULL OR request_hash ~ '^[0-9a-f]{64}$');

CREATE UNIQUE INDEX uq_resource_learning_check_idempotency
  ON resource_learning_check(user_id, resource_id, idempotency_key)
  WHERE idempotency_key IS NOT NULL;

COMMENT ON COLUMN resource_learning_check.idempotency_key IS
  'Optional client retry key, scoped by user and resource.';
COMMENT ON COLUMN resource_learning_check.request_hash IS
  'SHA-256 of the canonical submitted request; a key cannot be reused for different content.';
COMMENT ON COLUMN resource_learning_check.response_payload IS
  'Exact check response replayed for retries using the same idempotency key.';
