export interface SaveSlotInventoryItem {
  itemId: string;
  qty: number;
  slot: number;
}

/**
 * 后端 `save_slot.snapshot` JSONB blob，形如
 * `{ version, mainQuest: { currentChapter, currentSubQuest, completedSubQuests, ... } }`。
 * 主线进度跨设备同步读写此字段；mainQuest 用 unknown，由 mainQuest store 自行 safeParse。
 */
export interface SaveSlotSnapshot {
  version?: number;
  mainQuest?: unknown;
}

/**
 * 玩家存档槽。后端始终下发 sceneName / unlockedBuildings / accumulatedPlaySeconds /
 * inventoryItems 这 4 个 RPG 扩展字段（NOT NULL DEFAULT），故全部必填；inventoryItems 为
 * 背包快照，仅供存档恢复，不替代 InventoryStore 运行时态。
 */
export interface SaveSlotDto {
  slotIndex: number;
  displayName: string;
  isActive: boolean;
  createdAt: string;
  lastPlayedAt: string;
  /**
   * 存档快照（后端始终下发，至少是 `{}`）。设为可选以兼容历史 DTO mock，
   * 消费方（mainQuest.hydrateFromSnapshot）对缺失 / 空对象做安全忽略。
   */
  snapshot?: SaveSlotSnapshot | null;
  sceneName: string;
  unlockedBuildings: string[];
  accumulatedPlaySeconds: number;
  inventoryItems: SaveSlotInventoryItem[];
}
