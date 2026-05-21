package com.pythontopia.world.crop;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CropTile(
    Long id,
    Long userId,
    int tileX,
    int tileY,
    String kcCode,
    int growthStage,
    BigDecimal masteryScore,
    CropStatus status,
    boolean unlocked,
    OffsetDateTime unlockedAt,
    OffsetDateTime updatedAt,
    int packId) {}
