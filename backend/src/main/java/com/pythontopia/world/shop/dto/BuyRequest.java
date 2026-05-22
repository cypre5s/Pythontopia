package com.pythontopia.world.shop.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record BuyRequest(@NotBlank String itemKey, @Min(1) int qty) {}
