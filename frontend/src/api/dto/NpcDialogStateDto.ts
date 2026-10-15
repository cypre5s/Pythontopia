export interface NpcDialogStateDto {
  npcKey: string;
  currentNodeId: string | null;
  completedNodeIds: string[];
  lastTalkedAt: string | null;
  flags: Record<string, unknown>;
}

export interface NpcTalkRequest {
  nodeId: string;
  nextNodeId: string | null;
  effects: NpcDialogEffectPayload[];
}

// 与后端 `NpcDialogEffectDto(String type, String key, String value)` 对齐。
// 后端 SET_FLAG / COMPLETE_NODE 分支只读 key/value；历史字段 itemKey/questKey/flagKey 不被识别，
// 保留它们仅为兼容现有 dialog.json，新数据请用 key/value。
export interface NpcDialogEffectPayload {
  type: string;
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
  lastTalkedAt: string | null;
}
