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

export interface NpcDialogEffectPayload {
  type: string;
  delta?: number;
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
