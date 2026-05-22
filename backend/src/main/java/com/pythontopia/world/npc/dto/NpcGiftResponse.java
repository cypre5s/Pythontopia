package com.pythontopia.world.npc.dto;

public record NpcGiftResponse(
    String npcKey, String itemKey, String giftTier, int friendshipDelta, int newFriendship) {}
