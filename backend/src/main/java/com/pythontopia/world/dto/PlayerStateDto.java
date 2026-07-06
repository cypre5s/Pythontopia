package com.pythontopia.world.dto;

public record PlayerStateDto(
    Long playerStateId,
    String avatarKey,
    String mapKey,
    Integer tileX,
    Integer tileY,
    String direction) {}
