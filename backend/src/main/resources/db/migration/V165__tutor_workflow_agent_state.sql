CREATE TABLE tutor_workflow_agent_state (
  id BIGSERIAL PRIMARY KEY,
  session_id BIGINT NOT NULL REFERENCES tutor_workflow_session(id) ON DELETE CASCADE,
  state_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  latest_decision_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT tutor_workflow_agent_state_session_unique UNIQUE (session_id)
);

CREATE TABLE tutor_workflow_action_event (
  id BIGSERIAL PRIMARY KEY,
  session_id BIGINT NOT NULL REFERENCES tutor_workflow_session(id) ON DELETE CASCADE,
  seq INTEGER NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  action_key VARCHAR(64) NOT NULL,
  payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tutor_workflow_action_event_session_seq
  ON tutor_workflow_action_event(session_id, seq DESC);
