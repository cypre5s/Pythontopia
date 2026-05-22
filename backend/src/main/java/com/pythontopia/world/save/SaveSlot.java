package com.pythontopia.world.save;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;

public record SaveSlot(
    Long id,
    Long userId,
    Integer slotIndex,
    String displayName,
    Boolean isActive,
    OffsetDateTime createdAt,
    OffsetDateTime lastPlayedAt,
    JsonNode snapshot) {}
