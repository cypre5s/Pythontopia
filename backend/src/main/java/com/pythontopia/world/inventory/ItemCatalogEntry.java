package com.pythontopia.world.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;

public record ItemCatalogEntry(
    String itemKey,
    String displayName,
    String category,
    Integer basePrice,
    JsonNode giftTierMap,
    String description,
    OffsetDateTime createTime) {}
