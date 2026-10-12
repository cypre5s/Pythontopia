-- V197: Tutor workflow run projection idempotency marker.
--
-- Purpose: let one completed Graph run update session progress, agent checkpoint, card, and action
--   event in one transaction, while ignoring duplicate terminal callbacks for the same run_id.
-- Affected tables: adds tutor_workflow_run_projection; foreign key cleanup follows session deletion.
-- Irreversible risk: dropping this table removes deduplication history; later duplicate callbacks
--   could replay an already committed projection.

CREATE TABLE tutor_workflow_run_projection (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES tutor_workflow_session (id) ON DELETE CASCADE,
    run_id UUID NOT NULL,
    seq INTEGER NOT NULL,
    event VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_tutor_workflow_run_projection UNIQUE (session_id, run_id)
);

CREATE INDEX idx_tutor_workflow_run_projection_session_seq
    ON tutor_workflow_run_projection (session_id, seq DESC);
