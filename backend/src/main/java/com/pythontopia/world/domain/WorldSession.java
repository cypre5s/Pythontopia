package com.pythontopia.world.domain;

public record WorldSession(
    Long id, Long userId, Integer dayIndex, String weather, Integer inGameMinutes) {}
