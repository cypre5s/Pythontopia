package com.pythontopia.world.travel;

/**
 * R-12 W5 fast travel network: a single static row from {@code fast_travel_route} (migration
 * V119). Costs are pre-deducted at travel time; {@code costMinutes} is forwarded to the world
 * clock so a teleport still consumes in-game time, which preserves the day/night cadence.
 *
 * <p>See {@code docs/gameplay/r12-deepen/07-fast-travel-network.md} §3 for the design rationale.
 */
public record FastTravelRoute(
    Long id,
    String fromScene,
    String toScene,
    int unlockPhase,
    int costPythoco,
    int costEnergy,
    int costMinutes) {}
