package com.pythontopia.world.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PlayerStatePatch(
    @NotNull Integer tileX,
    @NotNull Integer tileY,
    @NotBlank @Pattern(regexp = "^(UP|DOWN|LEFT|RIGHT)$") String direction) {}
