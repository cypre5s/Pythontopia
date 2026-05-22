-- Purpose: persist per-learner NPC dialog node progress, friendship score, and custom flags.
-- Tables affected: npc_dialog_state.
-- Irreversible risk: dropping this table loses every learner's town NPC conversation history.

CREATE TABLE IF NOT EXISTS npc_dialog_state (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  npc_key VARCHAR(64) NOT NULL,
  current_node_id VARCHAR(128),
  completed_node_ids TEXT[] NOT NULL DEFAULT '{}',
  friendship INTEGER NOT NULL DEFAULT 0
    CONSTRAINT npc_dialog_state_friendship_range CHECK (friendship >= 0 AND friendship <= 100),
  last_talked_at TIMESTAMPTZ,
  flags JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_npc_dialog_user_npc UNIQUE (user_id, npc_key)
);

CREATE INDEX IF NOT EXISTS idx_npc_dialog_user ON npc_dialog_state(user_id);

COMMENT ON TABLE npc_dialog_state IS
  '每个玩家与每个 NPC 的对话节点进度、友好度、自定义 flags';
