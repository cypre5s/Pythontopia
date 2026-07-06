package com.pythontopia.world.festival;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;

/**
 * {@code world_festival} 目录（V118）的静态行，每个里程碑级游戏内日一行。
 *
 * <p>{@code overridePayload} 为 {@code null} 表示用默认值；非空时携带运行期调整（奖励覆盖、HUD 横幅文案、
 * BGM key），来自教师/运营经 {@code data/world/festivalCalendar.json} 的日程调整。
 */
public record WorldFestival(
    Long id,
    int dayIndex,
    String nameKey,
    String status,
    JsonNode overridePayload,
    OffsetDateTime createdAt) {}
