package com.pythontopia.admin;

import java.time.Instant;

public record AdminAiConfigDto(String provider, String model, boolean apiKeyConfigured, boolean keyStored, Instant updatedAt) {}
