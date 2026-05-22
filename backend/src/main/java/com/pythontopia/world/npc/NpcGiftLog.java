package com.pythontopia.world.npc;

import java.time.OffsetDateTime;

public record NpcGiftLog(
    Long id,
    Long userId,
    String npcKey,
    String itemKey,
    String giftTier,
    Integer friendshipDelta,
    OffsetDateTime givenAt) {}
