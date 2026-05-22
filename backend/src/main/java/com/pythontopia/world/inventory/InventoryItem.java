package com.pythontopia.world.inventory;

import java.time.OffsetDateTime;

public record InventoryItem(
    Long id, Long userId, String itemKey, Integer qty, OffsetDateTime updatedAt) {}
