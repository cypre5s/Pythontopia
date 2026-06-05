package com.pythontopia.world.travel;

/**
 * 快速旅行路线：{@code fast_travel_route}（迁移 V119）的一条静态行。花费在旅行时预扣；
 * {@code costMinutes} 转给世界时钟，使传送也消耗游戏内时间、保持昼夜节奏。
 */
public record FastTravelRoute(
    Long id,
    String fromScene,
    String toScene,
    int unlockPhase,
    int costPythoco,
    int costEnergy,
    int costMinutes) {}
