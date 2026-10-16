package com.pythontopia.world.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

// shortcut: bounds match the 50x40 town_hub; update them if its dimensions change.
public record PlayerStatePatch(
    @NotNull @Min(0) @Max(49) Integer tileX,
    @NotNull @Min(0) @Max(39) Integer tileY,
    @NotBlank
        @Pattern(regexp = "^(UP|DOWN|LEFT|RIGHT|UP_LEFT|UP_RIGHT|DOWN_LEFT|DOWN_RIGHT)$")
        String direction) {}
