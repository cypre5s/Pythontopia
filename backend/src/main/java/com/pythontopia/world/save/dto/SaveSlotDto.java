package com.pythontopia.world.save.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.pythontopia.world.save.SaveSlot;
import java.time.OffsetDateTime;
import java.util.List;

public record SaveSlotDto(
    int slotIndex,
    String displayName,
    boolean isActive,
    OffsetDateTime createdAt,
    OffsetDateTime lastPlayedAt,
    JsonNode snapshot,
    String sceneName,
    List<String> unlockedBuildings,
    long accumulatedPlaySeconds,
    JsonNode inventoryItems) {

  /**
   * Backward-compatible 6-arg constructor used by existing controller tests that predate the V114
   * extension columns. Delegates to the canonical constructor with the same defaults as the SQL
   * migration so JSON output stays consistent with a freshly migrated DB row.
   */
  public SaveSlotDto(
      int slotIndex,
      String displayName,
      boolean isActive,
      OffsetDateTime createdAt,
      OffsetDateTime lastPlayedAt,
      JsonNode snapshot) {
    this(
        slotIndex,
        displayName,
        isActive,
        createdAt,
        lastPlayedAt,
        snapshot,
        "WorldScene",
        List.of(),
        0L,
        null);
  }

  public static SaveSlotDto from(SaveSlot slot) {
    return new SaveSlotDto(
        slot.slotIndex() == null ? 0 : slot.slotIndex(),
        slot.displayName(),
        Boolean.TRUE.equals(slot.isActive()),
        slot.createdAt(),
        slot.lastPlayedAt(),
        slot.snapshot(),
        slot.sceneName() == null ? "WorldScene" : slot.sceneName(),
        slot.unlockedBuildings() == null ? List.of() : slot.unlockedBuildings(),
        slot.accumulatedPlaySeconds(),
        slot.inventoryItems());
  }
}
