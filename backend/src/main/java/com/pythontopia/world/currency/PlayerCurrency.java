package com.pythontopia.world.currency;

import java.time.OffsetDateTime;

public record PlayerCurrency(Long userId, Integer coins, Integer gems, OffsetDateTime updatedAt) {}
