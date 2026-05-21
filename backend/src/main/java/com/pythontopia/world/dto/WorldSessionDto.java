package com.pythontopia.world.dto;

import java.util.List;

public record WorldSessionDto(
    Long worldSessionId,
    Integer dayIndex,
    String weather,
    Integer inGameMinutes,
    PlayerStateDto player,
    List<NpcStateDto> npcs) {}
