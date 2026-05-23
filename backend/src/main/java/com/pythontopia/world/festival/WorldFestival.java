package com.pythontopia.world.festival;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;

/**
 * Static row of the V118 {@code world_festival} catalog. One row per milestone-tier in-game day
 * (see {@code docs/gameplay/r12-deepen/05-festival-events.md} §1 for the 11-entry timetable).
 *
 * <p>{@code overridePayload} is {@code null} when the festival uses defaults; it carries
 * runtime tweaks (reward overrides, HUD banner text, BGM key) when teachers / ops adjust the
 * schedule via {@code data/world/festivalCalendar.json}.
 */
public record WorldFestival(
    Long id,
    int dayIndex,
    String nameKey,
    String status,
    JsonNode overridePayload,
    OffsetDateTime createdAt) {}
