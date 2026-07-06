package com.pythontopia.world.dto;

import com.pythontopia.world.calendar.dto.WorldEventDto;
import java.util.List;

public record WorldAdvanceResponse(WorldSnapshotDto world, List<WorldEventDto> newEvents) {}
