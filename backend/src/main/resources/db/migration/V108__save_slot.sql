-- Purpose: support up to 6 named save slots per learner, with exactly one active slot enforced
--          via a partial unique index.
-- Tables affected: save_slot.
-- Irreversible risk: dropping save_slot destroys all snapshot backups taken by the player.

CREATE TABLE IF NOT EXISTS save_slot (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  slot_index INTEGER NOT NULL
    CONSTRAINT save_slot_index_range CHECK (slot_index BETWEEN 0 AND 5),
  display_name VARCHAR(64) NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_played_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
  CONSTRAINT uq_save_slot UNIQUE (user_id, slot_index)
);

CREATE INDEX IF NOT EXISTS idx_save_slot_user ON save_slot(user_id);

-- One-active-slot-per-user invariant (partial unique index).
CREATE UNIQUE INDEX IF NOT EXISTS uq_save_slot_active
  ON save_slot(user_id)
  WHERE is_active = true;

COMMENT ON TABLE save_slot IS '玩家本地存档槽，最多 6 个，is_active=true 唯一';
