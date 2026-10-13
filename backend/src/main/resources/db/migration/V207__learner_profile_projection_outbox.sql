-- Purpose: persist passive learner-profile projections so failed dimension updates can be retried.
-- Tables affected: learner_profile_projection_outbox (new).
-- Irreversible risk: dropping this table loses pending updates and applied idempotency keys; restore from a database backup.

CREATE TABLE learner_profile_projection_outbox (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  idempotency_key VARCHAR(512) NOT NULL UNIQUE,
  dimension_key VARCHAR(64) NOT NULL CHECK (dimension_key IN (
    'professional_background', 'knowledge_base', 'learning_goal', 'cognitive_style',
    'learning_preference', 'error_pattern', 'practical_ability', 'emotion_persistence'
  )),
  delta JSONB NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPLIED')),
  attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
  error_kind VARCHAR(128),
  next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  processed_at TIMESTAMPTZ
);

CREATE INDEX idx_profile_projection_outbox_pending
  ON learner_profile_projection_outbox (next_attempt_at, id)
  WHERE status = 'PENDING';

CREATE INDEX idx_profile_projection_outbox_user_order
  ON learner_profile_projection_outbox (user_id, id)
  WHERE status = 'PENDING';

COMMENT ON TABLE learner_profile_projection_outbox
  IS 'Durable, ordered and idempotent passive learner profile updates; pending rows are retried after transient failures.';
