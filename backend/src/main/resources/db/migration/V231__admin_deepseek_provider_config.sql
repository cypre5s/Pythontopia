-- Purpose: persist the administrator-managed DeepSeek model and optional encrypted API key.
-- Tables affected: creates ai_provider_config with one singleton row and an admin audit reference.
-- Irreversible risk: dropping the table loses the active model and encrypted provider key; deployment env values remain unchanged.
CREATE TABLE ai_provider_config (
  singleton_id SMALLINT PRIMARY KEY DEFAULT 1 CHECK (singleton_id = 1),
  model VARCHAR(32) NOT NULL CHECK (model IN ('deepseek-flash', 'deepseek-v4-pro')),
  encrypted_api_key TEXT,
  updated_by BIGINT NOT NULL REFERENCES users(id),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
