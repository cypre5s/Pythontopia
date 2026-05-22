package com.pythontopia.world.save.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.pythontopia.world.save.SaveSlot;
import java.time.OffsetDateTime;

public record SaveSlotDto(
    int slotIndex,
    String displayName,
    boolean isActive,
    OffsetDateTime createdAt,
    OffsetDateTime lastPlayedAt,
    JsonNode snapshot) {

  public static SaveSlotDto from(SaveSlot slot) {
    return new SaveSlotDto(
        slot.slotIndex() == null ? 0 : slot.slotIndex(),
        slot.displayName(),
        Boolean.TRUE.equals(slot.isActive()),
        slot.createdAt(),
        slot.lastPlayedAt(),
        slot.snapshot());
  }
}
