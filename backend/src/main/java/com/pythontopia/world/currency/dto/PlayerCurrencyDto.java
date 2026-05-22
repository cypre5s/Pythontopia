package com.pythontopia.world.currency.dto;

import com.pythontopia.world.currency.PlayerCurrency;
import java.time.OffsetDateTime;

public record PlayerCurrencyDto(int coins, int gems, OffsetDateTime updatedAt) {

  public static PlayerCurrencyDto from(PlayerCurrency currency) {
    return new PlayerCurrencyDto(
        currency.coins() == null ? 0 : currency.coins(),
        currency.gems() == null ? 0 : currency.gems(),
        currency.updatedAt());
  }
}
