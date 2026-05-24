export interface NpcDialogStateDto {
  npcKey: string;
  currentNodeId: string | null;
  completedNodeIds: string[];
  friendship: number;
  lastTalkedAt: string | null;
  flags: Record<string, unknown>;
}

export interface NpcTalkRequest {
  nodeId: string;
  nextNodeId: string | null;
  effects: NpcDialogEffectPayload[];
}

// R-13：与后端 `NpcDialogEffectDto(String type, Integer delta, String key, String value)` 对齐。
// 后端在 SET_FLAG / COMPLETE_NODE 分支只读 `key`/`value`，前端历史字段 itemKey/questKey/
// flagKey 不会被识别，导致这两类效果实际不生效。这里加 `key`/`value` 为首选，旧字段保留
// 兜底，避免一次性破坏现有 dialog.json 数据。
export interface NpcDialogEffectPayload {
  type: string;
  delta?: number;
  /** 后端 NpcDialogService.SET_FLAG / COMPLETE_NODE 实际读的字段 */
  key?: string;
  /** 后端 SET_FLAG 写入 npc_dialog_state.flags 的 value */
  value?: string;
  /** 历史前端字段，建议改用 key */
  itemKey?: string;
  questKey?: string;
  flagKey?: string;
  route?: string;
}

export interface NpcTalkResponseDto {
  npcKey: string;
  currentNodeId: string | null;
  completedNodeIds: string[];
  friendship: number;
  lastTalkedAt: string | null;
}

export interface NpcGiftRequest {
  itemKey: string;
  qty: number;
}

export type NpcGiftTier = 'LOVE' | 'LIKE' | 'NEUTRAL' | 'DISLIKE' | 'HATE';

export interface NpcGiftResponseDto {
  npcKey: string;
  itemKey: string;
  giftTier: NpcGiftTier;
  friendshipDelta: number;
  newFriendship: number;
}
