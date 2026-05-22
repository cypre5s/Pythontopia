package com.pythontopia.world.inventory.dto;

import jakarta.validation.constraints.Min;

public record InventoryGrantRequest(@Min(1) int qty) {}
