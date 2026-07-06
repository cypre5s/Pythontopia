package com.pythontopia.world.calendar.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.pythontopia.world.calendar.WorldEvent;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record WorldEventDto(
    Long id,
    LocalDate dateIso,
    String eventType,
    JsonNode payload,
    boolean isProcessed,
    OffsetDateTime createdAt) {

  public static WorldEventDto from(WorldEvent event) {
    return new WorldEventDto(
        event.id(),
        event.dateIso(),
        event.eventType(),
        event.payload(),
        Boolean.TRUE.equals(event.isProcessed()),
        event.createdAt());
  }
}
