package com.pythontopia.world.inventory.dto;

import com.pythontopia.world.inventory.InventoryItem;
import java.time.OffsetDateTime;

public record InventoryItemDto(String itemKey, int qty, OffsetDateTime updatedAt) {

  public static InventoryItemDto from(InventoryItem item) {
    return new InventoryItemDto(
        item.itemKey(), item.qty() == null ? 0 : item.qty(), item.updatedAt());
  }
}
