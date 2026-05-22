package com.pythontopia.world.shop.dto;

import com.pythontopia.world.currency.dto.PlayerCurrencyDto;
import com.pythontopia.world.inventory.dto.InventoryItemDto;

public record BuyResponse(InventoryItemDto item, PlayerCurrencyDto currency) {}
