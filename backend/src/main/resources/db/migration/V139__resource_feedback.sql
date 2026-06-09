-- resource_feedback: student rating + adoption signals for meta-reflection.
CREATE TABLE IF NOT EXISTS resource_feedback (
  id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL, resource_id BIGINT,
  agent_name VARCHAR(64) NOT NULL, rating SMALLINT CHECK (rating>=1 AND rating<=5),
  adopted BOOLEAN NOT NULL DEFAULT false, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_rf_agent ON resource_feedback(agent_name, created_at);
