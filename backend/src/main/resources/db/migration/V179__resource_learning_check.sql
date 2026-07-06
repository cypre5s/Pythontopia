-- Purpose: persist resource-consumption follow-up evidence so mastery changes require a micro-check or operation proof.
-- Tables affected: kc_mastery_event（扩展 source CHECK）, resource_learning_check（新增）
-- Irreversible risk: dropping resource_learning_check loses the audit trail for resource micro-checks; mastery history remains in kc_mastery_event.

ALTER TABLE kc_mastery_event
  DROP CONSTRAINT IF EXISTS kc_mastery_event_source_check;

ALTER TABLE kc_mastery_event
  ADD CONSTRAINT kc_mastery_event_source_check
    CHECK (
      source IN (
        'RESOURCE_READ',
        'PRACTICE_PASS',
        'PRACTICE_FAIL',
        'DEBUG_FIX',
        'MANUAL_OVERRIDE',
        'DECAY',
        'BOOTSTRAP',
        'MICRO_CHECK_PASS',
        'MICRO_CHECK_FAIL'
      )
    );

CREATE TABLE IF NOT EXISTS resource_learning_check (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  resource_id BIGINT NOT NULL REFERENCES learning_resource(id) ON DELETE CASCADE,
  kc_code VARCHAR(64) NOT NULL,
  evidence_type VARCHAR(16) NOT NULL CHECK (evidence_type IN ('MICRO_QUIZ', 'OPERATION')),
  answer_text VARCHAR(2000),
  operation_evidence JSONB NOT NULL DEFAULT '{}'::jsonb,
  status VARCHAR(16) NOT NULL CHECK (status IN ('PASSED', 'FAILED')),
  mastery_delta NUMERIC(5, 3) NOT NULL DEFAULT 0.000,
  remediation_type VARCHAR(16) CHECK (
    remediation_type IS NULL OR remediation_type IN ('HANDOUT', 'MINDMAP', 'PRACTICE', 'CODE_LAB', 'READING', 'STORYBOARD')
  ),
  reason VARCHAR(256),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_resource_learning_check_user_resource
  ON resource_learning_check(user_id, resource_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_resource_learning_check_user_status
  ON resource_learning_check(user_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_resource_learning_check_kc
  ON resource_learning_check(user_id, kc_code, created_at DESC);
