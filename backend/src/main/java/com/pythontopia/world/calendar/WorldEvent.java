package com.pythontopia.world.calendar;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record WorldEvent(
    Long id,
    Long userId,
    LocalDate dateIso,
    String eventType,
    JsonNode payload,
    Boolean isProcessed,
    OffsetDateTime createdAt) {}
