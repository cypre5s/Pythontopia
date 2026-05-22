package com.pythontopia.world.npc.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record NpcGiftRequest(@NotBlank String itemKey, @Min(1) int qty) {}
