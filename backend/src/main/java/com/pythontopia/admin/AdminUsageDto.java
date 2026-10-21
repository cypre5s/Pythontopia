package com.pythontopia.admin;

import java.time.Instant;
import java.util.List;

public record AdminUsageDto(
    List<Event> content,
    int days,
    long totalEvents,
    long inputTokens,
    long outputTokens,
    long unknownTokenEvents) {

  public record Event(
      long id,
      String eventType,
      long userId,
      String username,
      String label,
      Instant occurredAt,
      String status,
      Integer inputTokens,
      Integer outputTokens) {}
}
