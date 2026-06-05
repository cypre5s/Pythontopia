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
   * 向后兼容的 6 参数构造器，供 V114 扩展列之前的 controller 测试使用。委托到规范构造器并采用与 SQL
   * migration 相同的默认值，使 JSON 输出与刚迁移的 DB 行保持一致。
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
