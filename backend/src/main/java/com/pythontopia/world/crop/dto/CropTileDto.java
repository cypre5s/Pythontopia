package com.pythontopia.world.crop.dto;

import java.math.BigDecimal;

public record CropTileDto(
    int tileX,
    int tileY,
    String kcCode,
    String status,
    boolean unlocked,
    BigDecimal masteryScore,
    int growthStage) {}
