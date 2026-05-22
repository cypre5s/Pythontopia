package com.pythontopia.world.shop;

import java.util.List;

public record ShopListing(
    Long id,
    String shopKey,
    String itemKey,
    Integer priceCoins,
    Integer priceGems,
    Integer stock,
    List<Integer> availableDays) {}
