package com.pythontopia.world.shop.dto;

import com.pythontopia.world.shop.ShopListing;
import java.util.List;

public record ShopListingDto(
    String shopKey,
    String itemKey,
    int priceCoins,
    int priceGems,
    Integer stock,
    List<Integer> availableDays) {

  public static ShopListingDto from(ShopListing listing) {
    return new ShopListingDto(
        listing.shopKey(),
        listing.itemKey(),
        listing.priceCoins() == null ? 0 : listing.priceCoins(),
        listing.priceGems() == null ? 0 : listing.priceGems(),
        listing.stock(),
        listing.availableDays());
  }
}
