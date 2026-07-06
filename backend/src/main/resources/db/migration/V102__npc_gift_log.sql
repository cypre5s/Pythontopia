-- Purpose: append-only log of gifts given to NPCs, used by ProfileAgent to track relationship signals.
-- Tables affected: npc_gift_log.
-- Irreversible risk: dropping this table loses the audit trail of friendship deltas.

CREATE TABLE IF NOT EXISTS npc_gift_log (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  npc_key VARCHAR(64) NOT NULL,
  item_key VARCHAR(64) NOT NULL,
  gift_tier VARCHAR(16) NOT NULL
    CONSTRAINT npc_gift_log_tier_check
      CHECK (gift_tier IN ('love', 'like', 'neutral', 'dislike', 'hate')),
  friendship_delta INTEGER NOT NULL,
  given_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_npc_gift_log_user_npc ON npc_gift_log(user_id, npc_key);
CREATE INDEX IF NOT EXISTS idx_npc_gift_log_user_time
  ON npc_gift_log(user_id, given_at DESC);

COMMENT ON TABLE npc_gift_log IS '玩家送礼记录，用于复盘 NPC 好感度变化';
