-- Purpose: NPC 个性化记忆/人格/好感度，支撑「NPC 记住你」与后续费曼 AI 学弟。
-- Tables affected: 新建 npc_memory / npc_persona / npc_affinity。
-- Irreversible risk: drop 丢失 NPC 记忆关联与好感度；可由 learning_event_log 重建关联但会丢失手工人格增补。
CREATE TABLE IF NOT EXISTS npc_memory (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  npc_key VARCHAR(40) NOT NULL,
  event_id BIGINT NOT NULL REFERENCES learning_event_log(id) ON DELETE CASCADE,
  salience DOUBLE PRECISION NOT NULL DEFAULT 0.5,
  superseded_by BIGINT NULL REFERENCES npc_memory(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_recalled_at TIMESTAMPTZ NULL,
  UNIQUE (user_id, npc_key, event_id)
);

CREATE INDEX IF NOT EXISTS idx_npc_memory_user_npc
  ON npc_memory(user_id, npc_key, created_at DESC);

CREATE TABLE IF NOT EXISTS npc_persona (
  npc_key VARCHAR(40) PRIMARY KEY,
  display_name VARCHAR(80) NOT NULL,
  core_md TEXT NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS npc_affinity (
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  npc_key VARCHAR(40) NOT NULL,
  score DOUBLE PRECISION NOT NULL DEFAULT 0,
  tier SMALLINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, npc_key)
);
