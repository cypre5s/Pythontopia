-- Purpose: agent 自我进化策略表，每个 agent 的可调策略参数 + 版本 + 审计理由。
-- Tables affected: agent_strategy (new).
-- Irreversible risk: none (only creates table).

CREATE TABLE IF NOT EXISTS agent_strategy (
  id BIGSERIAL PRIMARY KEY,
  agent_name VARCHAR(64) NOT NULL,
  strategy_params JSONB NOT NULL DEFAULT '{}'::jsonb,
  version INT NOT NULL DEFAULT 1,
  reason TEXT NOT NULL DEFAULT '',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_agent_strategy_latest
  ON agent_strategy(agent_name, version DESC);
