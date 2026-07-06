-- Purpose: drop the NPC friendship score + coupled gift surface (R-14 world-shrink follow-up).
--          The friendship/gift loop (talk/gift to raise a 0..100 heart score that gated special
--          dialog nodes) has been removed across all clients; this migration retires the backing
--          storage so npc_dialog_state no longer carries a friendship column and the gift-log /
--          relationship cache tables are gone. Node progress + flags in npc_dialog_state stay.
-- Tables affected: npc_dialog_state (drops the friendship column + its CHECK constraint
--                  npc_dialog_state_friendship_range added in V101),
--                  npc_gift_log (dropped; created in V102, re-declared in V117),
--                  npc_relationship (dropped; created in V117).
-- Irreversible risk: every learner's friendship score, gift history and relationship cache are
--                    permanently discarded and cannot be rebuilt; npc_dialog_state node progress
--                    and flags are preserved.

ALTER TABLE npc_dialog_state DROP COLUMN IF EXISTS friendship;

DROP TABLE IF EXISTS npc_gift_log;
DROP TABLE IF EXISTS npc_relationship;
