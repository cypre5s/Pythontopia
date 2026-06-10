export interface SaveSlotInventoryItem {
  itemId: string;
  qty: number;
  slot: number;
}

/**
 * 后端 `save_slot.snapshot` JSONB blob。
 * R-15 起旧 `mainQuest` 子键已弃用（主线进度改由后端权威 mainline 域，按 user_id 独立表存储），
 * 前端不再读写它；保留 `version` 以兼容快照版本协商。历史快照里的 `mainQuest` 字段会被自然忽略。
 */
export interface SaveSlotSnapshot {
  version?: number;
}

/**
 * 玩家存档槽。后端始终下发 sceneName / unlockedBuildings / accumulatedPlaySeconds /
 * inventoryItems 这 4 个 RPG 扩展字段（NOT NULL DEFAULT），故全部必填。
 * R-14 收缩后前端无背包/建筑解锁玩法，unlockedBuildings/inventoryItems 仅作为
 * 后端契约字段透传，前端不消费。
 */
export interface SaveSlotDto {
  slotIndex: number;
  displayName: string;
  isActive: boolean;
  createdAt: string;
  lastPlayedAt: string;
  /**
   * 存档快照（后端始终下发，至少是 `{}`）。设为可选以兼容历史 DTO mock。
   * R-15 起前端不消费 snapshot 内的主线进度（已迁出到 mainline 域）。
   */
  snapshot?: SaveSlotSnapshot | null;
  sceneName: string;
  unlockedBuildings: string[];
  accumulatedPlaySeconds: number;
  inventoryItems: SaveSlotInventoryItem[];
}
