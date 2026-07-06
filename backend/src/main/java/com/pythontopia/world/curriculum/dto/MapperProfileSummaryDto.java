package com.pythontopia.world.curriculum.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record MapperProfileSummaryDto(
    String major,
    @JsonProperty("python_self_rated_level") int pythonSelfRatedLevel,
    @JsonProperty("target_kc_codes") List<String> targetKcCodes,
    @JsonProperty("media_priority") List<String> mediaPriority) {}
