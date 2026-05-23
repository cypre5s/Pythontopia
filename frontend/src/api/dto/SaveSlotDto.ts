/**
 * 玩家存档槽 DTO。V114 之后后端会始终下发 4 个 RPG 扩展字段（NOT NULL DEFAULT），所以这里全部
 * 设为必填以强制前端契约。
 *
 * - `sceneName`：当前 Phaser scene key（用于跨刷新恢复地图）
 * - `unlockedBuildings`：已解锁建筑 key 列表（用于地图门禁判定）
 * - `accumulatedPlaySeconds`：累计游玩秒数（用于成就 / 班级看板）
 * - `inventoryItems`：背包快照 `{ itemId, qty, slot }[]`（仅存档恢复用，不替代 InventoryStore 运行时态）
 */
export interface SaveSlotInventoryItem {
  itemId: string;
  qty: number;
  slot: number;
}

export interface SaveSlotDto {
  slotIndex: number;
  displayName: string;
  isActive: boolean;
  createdAt: string;
  lastPlayedAt: string;
  sceneName: string;
  unlockedBuildings: string[];
  accumulatedPlaySeconds: number;
  inventoryItems: SaveSlotInventoryItem[];
}
