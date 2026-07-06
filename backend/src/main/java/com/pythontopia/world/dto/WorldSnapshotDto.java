package com.pythontopia.world.dto;

import java.time.OffsetDateTime;

/**
 * 含日历信息的世界快照，供 {@code /api/game/bootstrap} 与 {@code /api/world/sleep} 使用；
 * 旧端点 {@code /api/world/session} 仍使用更精简的 {@link WorldSessionDto}。
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
