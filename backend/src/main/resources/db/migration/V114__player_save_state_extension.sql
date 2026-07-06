-- W3 todo `w3_save_state` 扩展存档 schema：场景定位、解锁建筑、累计游玩时长、背包快照。
-- Tables affected: save_slot（V108 建立的玩家存档槽）。
-- Irreversible risk: 删除新列会丢失玩家的当前场景、解锁进度、累计时长和背包快照。
--
-- 注意：W3 spec 草稿曾以 `player_save_state` 为占位表名，实际持久层一直使用 `save_slot`，
-- 这里以真实表名为准，不再引入冗余表名。

ALTER TABLE save_slot
  ADD COLUMN IF NOT EXISTS scene_name VARCHAR(64) NOT NULL DEFAULT 'WorldScene',
  ADD COLUMN IF NOT EXISTS unlocked_buildings TEXT[] NOT NULL DEFAULT '{}',
  ADD COLUMN IF NOT EXISTS accumulated_play_seconds BIGINT NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS inventory_items JSONB NOT NULL DEFAULT '[]'::jsonb;

CREATE INDEX IF NOT EXISTS idx_save_slot_scene
  ON save_slot (user_id, scene_name);

COMMENT ON COLUMN save_slot.scene_name IS 'W3 当前所处 Phaser scene key';
COMMENT ON COLUMN save_slot.unlocked_buildings IS 'W3 玩家已解锁建筑列表';
COMMENT ON COLUMN save_slot.accumulated_play_seconds IS 'W3 累计游玩时长 (秒)';
COMMENT ON COLUMN save_slot.inventory_items IS 'W3 背包道具 JSON 数组 [{itemId, qty, slot}]';
