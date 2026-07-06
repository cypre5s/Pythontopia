package com.pythontopia.world.curriculum.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record MapperResponse(
    @JsonProperty("plan_steps") List<PlanStepDto> planSteps,
    @JsonProperty("tile_assignments") List<TileAssignmentDto> tileAssignments,
    @JsonProperty("unlocked_kcs") List<String> unlockedKcs,
    @JsonProperty("deterministic_signature") String deterministicSignature) {}
