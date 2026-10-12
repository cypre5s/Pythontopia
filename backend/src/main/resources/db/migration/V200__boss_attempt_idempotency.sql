-- V200: persist idempotency claims and results for boss and endless attempts.
-- Purpose: replaying one web submission must not rerun judging or publish learning events twice.
-- Affected tables: creates boss_attempt_idempotency, linked to users.
-- Irreversible risk: dropping the table removes replay protection for stored attempt IDs.

CREATE TABLE boss_attempt_idempotency (
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    attempt_id UUID NOT NULL,
    request_hash CHAR(64) NOT NULL,
    claim_token UUID NOT NULL,
    response_payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, attempt_id)
);

CREATE INDEX idx_boss_attempt_idempotency_updated_at
    ON boss_attempt_idempotency (updated_at);
