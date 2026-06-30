-- Purpose: Phase 1 Agentic Runtime control plane state and policy-decision records.
-- Tables affected: loop_state, policy_decision (new tables only).
-- Irreversible risk: dropping these tables loses runtime state snapshots and policy-decision audit records.
-- Rollback plan: forward migration that archives and drops the two tables if this control plane is retired.

CREATE TABLE loop_state (
    id BIGSERIAL PRIMARY KEY,
    loop_run_id BIGINT NOT NULL REFERENCES loop_run(id) ON DELETE CASCADE,
    loop_type TEXT NOT NULL,
    state_key TEXT NOT NULL,
    status TEXT NOT NULL,
    state JSONB NOT NULL DEFAULT '{}'::jsonb,
    policy_version TEXT NOT NULL,
    trace_id TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT loop_state_type_valid CHECK (
        loop_type IN ('LEARNER', 'DEBUG', 'RESOURCE', 'WORLD', 'IMPROVE')
    ),
    CONSTRAINT loop_state_key_nonblank CHECK (length(trim(state_key)) > 0),
    CONSTRAINT loop_state_status_nonblank CHECK (length(trim(status)) > 0),
    CONSTRAINT loop_state_policy_nonblank CHECK (length(trim(policy_version)) > 0),
    CONSTRAINT loop_state_trace_nonblank CHECK (length(trim(trace_id)) > 0),
    CONSTRAINT uq_loop_state_key UNIQUE (loop_run_id, state_key)
);

CREATE INDEX idx_loop_state_run_order
    ON loop_state (loop_run_id, updated_at DESC, id DESC);

CREATE INDEX idx_loop_state_type_policy
    ON loop_state (loop_type, policy_version, updated_at DESC);

CREATE TABLE policy_decision (
    id BIGSERIAL PRIMARY KEY,
    loop_run_id BIGINT NOT NULL REFERENCES loop_run(id) ON DELETE CASCADE,
    decision_key TEXT NOT NULL,
    policy_version TEXT NOT NULL,
    risk_level TEXT NOT NULL,
    approval_required BOOLEAN NOT NULL DEFAULT false,
    decision JSONB NOT NULL DEFAULT '{}'::jsonb,
    reason TEXT NOT NULL DEFAULT '',
    trace_id TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT policy_decision_key_nonblank CHECK (length(trim(decision_key)) > 0),
    CONSTRAINT policy_decision_policy_nonblank CHECK (length(trim(policy_version)) > 0),
    CONSTRAINT policy_decision_risk_valid CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT policy_decision_trace_nonblank CHECK (length(trim(trace_id)) > 0)
);

CREATE INDEX idx_policy_decision_run_order
    ON policy_decision (loop_run_id, id);

CREATE INDEX idx_policy_decision_policy_risk
    ON policy_decision (policy_version, risk_level, created_at DESC);
