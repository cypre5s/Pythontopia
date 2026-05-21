package com.pythontopia.world.domain;

public record PlayerState(
    Long id,
    Long userId,
    String avatarKey,
    String mapKey,
    Integer tileX,
    Integer tileY,
    String direction) {}
