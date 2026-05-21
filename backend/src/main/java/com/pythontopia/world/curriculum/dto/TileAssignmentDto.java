package com.pythontopia.world.curriculum.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TileAssignmentDto(
    @JsonProperty("kc_code") String kcCode,
    @JsonProperty("tile_x") int tileX,
    @JsonProperty("tile_y") int tileY,
    @JsonProperty("chapter_id") long chapterId) {}
