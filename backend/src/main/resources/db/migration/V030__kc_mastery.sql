-- Purpose: track per-learner knowledge component mastery and append-only event log.
-- Tables affected: kc_mastery, kc_mastery_event.
-- Irreversible risk: dropping these tables loses all learner mastery history.

CREATE TABLE IF NOT EXISTS kc_mastery (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  kc_code VARCHAR(64) NOT NULL,
  mastery_score NUMERIC(4, 3) NOT NULL DEFAULT 0.000,
  confidence NUMERIC(4, 3) NOT NULL DEFAULT 0.000,
  last_event_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT kc_mastery_score_range CHECK (mastery_score >= 0.0 AND mastery_score <= 1.0),
  CONSTRAINT kc_mastery_confidence_range CHECK (confidence >= 0.0 AND confidence <= 1.0),
  UNIQUE (user_id, kc_code)
);

CREATE INDEX IF NOT EXISTS idx_kc_mastery_user ON kc_mastery(user_id);

CREATE TABLE IF NOT EXISTS kc_mastery_event (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  kc_code VARCHAR(64) NOT NULL,
  source VARCHAR(32) NOT NULL CHECK (
    source IN (
      'RESOURCE_READ',
      'PRACTICE_PASS',
      'PRACTICE_FAIL',
      'DEBUG_FIX',
      'MANUAL_OVERRIDE',
      'DECAY',
      'BOOTSTRAP'
    )
  ),
  delta NUMERIC(5, 3) NOT NULL,
  reason VARCHAR(256),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_kc_mastery_event_user_kc ON kc_mastery_event(user_id, kc_code, created_at DESC);
