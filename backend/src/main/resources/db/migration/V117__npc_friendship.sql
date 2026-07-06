-- Purpose: standalone NPC friendship cache + gift log skeleton for R-12 deepen pass.
--          npc_relationship persists per-learner × per-NPC friendship score plus a weekly
--          delta accumulator used by NpcRelationshipService to enforce the ±10 weekly cap
--          described in docs/gameplay/r12-deepen/06-gift-table.md §2.1.
--          npc_gift_log is re-declared idempotently so this migration is self-contained
--          for new environments; when V102 already created the table (with the richer
--          gift_tier CHECK + FK columns) the IF NOT EXISTS guard keeps the existing
--          definition intact, and only the columns referenced by this skeleton (user_id,
--          npc_key, item_key, given_at, friendship_delta) are read or written.
-- Tables affected: npc_relationship (new), npc_gift_log (already present since V102 → no-op
--                  on existing databases; created fresh on greenfield installs).
-- Irreversible risk: dropping npc_relationship discards every learner's friendship cache
--                    and weekly window aggregate; downstream gift-mode UI, dialog gating,
--                    soulmate buffs and PassiveProfileService will reset to friendship=0
--                    until rebuilt by replaying npc_gift_log.

CREATE TABLE IF NOT EXISTS npc_relationship (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  npc_key VARCHAR(64) NOT NULL,
  friendship INTEGER NOT NULL DEFAULT 0,
  last_gift_day INTEGER,
  weekly_delta_sum INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  CONSTRAINT uq_npc_relationship_user_npc UNIQUE (user_id, npc_key)
);

CREATE INDEX IF NOT EXISTS idx_npc_relationship_user_npc
  ON npc_relationship(user_id, npc_key);

CREATE TABLE IF NOT EXISTS npc_gift_log (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  npc_key VARCHAR(64) NOT NULL,
  item_key VARCHAR(64) NOT NULL,
  given_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  friendship_delta INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_npc_gift_log_user_npc
  ON npc_gift_log(user_id, npc_key);
CREATE INDEX IF NOT EXISTS idx_npc_gift_log_user_time
  ON npc_gift_log(user_id, given_at DESC);

COMMENT ON TABLE npc_relationship IS
  '玩家 × NPC 好感度缓存表：friendship ∈ [0,100]，weekly_delta_sum 配合 last_gift_day 实现单 NPC 单周 ±10 净增量上限钳制 (06-gift-table §2.1)。';
COMMENT ON COLUMN npc_relationship.last_gift_day IS
  '上次写入时的游戏日 (WorldClock.dayIndex, 1–112)，service 据此判定是否跨周需要归零 weekly_delta_sum。';
COMMENT ON COLUMN npc_relationship.weekly_delta_sum IS
  '当前 7 日窗口内 friendship 净变化代数和；service 钳制时以 npc_gift_log 累计为权威，本列仅作冗余缓存。';
