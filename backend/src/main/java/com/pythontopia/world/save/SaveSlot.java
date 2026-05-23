package com.pythontopia.world.save;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.List;

public record SaveSlot(
    Long id,
    Long userId,
    Integer slotIndex,
    String displayName,
    Boolean isActive,
    OffsetDateTime createdAt,
    OffsetDateTime lastPlayedAt,
    JsonNode snapshot,
    String sceneName,
    List<String> unlockedBuildings,
    long accumulatedPlaySeconds,
    JsonNode inventoryItems) {}
