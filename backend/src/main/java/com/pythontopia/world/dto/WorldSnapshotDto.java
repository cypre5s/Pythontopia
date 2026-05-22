package com.pythontopia.world.dto;

import java.time.OffsetDateTime;

/**
 * Calendar-aware world snapshot used by {@code /api/game/bootstrap} and {@code /api/world/sleep}.
 * The narrower {@link WorldSessionDto} is kept untouched for legacy {@code /api/world/session}
 * callers.
 */
public record WorldSnapshotDto(
    Long worldSessionId,
    int dayIndex,
    int monthIndex,
    String weekday,
    String season,
    String timeOfDay,
    String weather,
    int inGameMinutes,
    OffsetDateTime startedAt,
    OffsetDateTime lastActiveAt) {}
