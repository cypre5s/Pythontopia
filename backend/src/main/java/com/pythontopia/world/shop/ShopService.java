package com.pythontopia.world.shop;

import com.pythontopia.common.error.BusinessException;
import com.pythontopia.world.calendar.CalendarMath;
import com.pythontopia.world.currency.CurrencyRepository;
import com.pythontopia.world.currency.dto.PlayerCurrencyDto;
import com.pythontopia.world.inventory.InventoryRepository;
import com.pythontopia.world.inventory.dto.InventoryItemDto;
import com.pythontopia.world.repository.GameBootstrapRepository;
import com.pythontopia.world.shop.dto.BuyRequest;
import com.pythontopia.world.shop.dto.BuyResponse;
import com.pythontopia.world.shop.dto.ShopListingDto;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShopService {

  private final ShopListingRepository listingRepository;
  private final InventoryRepository inventoryRepository;
  private final CurrencyRepository currencyRepository;
  private final GameBootstrapRepository bootstrapRepository;

  public ShopService(
      ShopListingRepository listingRepository,
      InventoryRepository inventoryRepository,
      CurrencyRepository currencyRepository,
      GameBootstrapRepository bootstrapRepository) {
    this.listingRepository = listingRepository;
    this.inventoryRepository = inventoryRepository;
    this.currencyRepository = currencyRepository;
    this.bootstrapRepository = bootstrapRepository;
  }

  public List<ShopListingDto> listing(Long userId, String shopKey) {
    int weekdayIdx = currentWeekdayIndex(userId);
    return listingRepository.findByShop(shopKey).stream()
        .filter(listing -> isAvailable(listing, weekdayIdx))
        .map(ShopListingDto::from)
        .toList();
  }

  @Transactional
  public BuyResponse buy(Long userId, String shopKey, BuyRequest request) {
    if (request.qty() <= 0) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "SHOP_QTY_INVALID", "Purchase qty must be positive");
    }
    ShopListing listing =
        listingRepository
            .findOne(shopKey, request.itemKey())
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "SHOP_LISTING_NOT_FOUND",
                        "Shop " + shopKey + " does not sell " + request.itemKey()));
    int weekdayIdx = currentWeekdayIndex(userId);
    if (!isAvailable(listing, weekdayIdx)) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "SHOP_CLOSED_TODAY", "This SKU is not available today");
    }
    inventoryRepository
        .findCatalogEntry(request.itemKey())
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "Unknown item " + request.itemKey()));

    int totalCoins = Math.multiplyExact(listing.priceCoins(), request.qty());
    var currency =
        currencyRepository.deductCoins(
            userId,
            totalCoins,
            "SHOP_PURCHASE",
            Map.of(
                "shop", shopKey,
                "itemKey", request.itemKey(),
                "qty", request.qty(),
                "unitPrice", listing.priceCoins()));
    var item = inventoryRepository.addItem(userId, request.itemKey(), request.qty());
    if (listing.stock() != null) {
      listingRepository.decrementStock(listing.id(), request.qty());
    }
    return new BuyResponse(InventoryItemDto.from(item), PlayerCurrencyDto.from(currency));
  }

  private boolean isAvailable(ShopListing listing, int weekdayIdx) {
    List<Integer> days = listing.availableDays();
    if (days == null || days.isEmpty()) {
      return true;
    }
    return days.contains(weekdayIdx);
  }

  private int currentWeekdayIndex(Long userId) {
    return bootstrapRepository
        .findRow(userId)
        .map(GameBootstrapRepository.BootstrapRow::world)
        .filter(world -> world != null)
        .map(world -> CalendarMath.weekdayIndex(world.weekday()))
        .orElse(1);
  }
}
