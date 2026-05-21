package com.pythontopia.world.curriculum.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PlanStepDto(
    int order,
    @JsonProperty("kc_code") String kcCode,
    @JsonProperty("chapter_order") int chapterOrder,
    String rationale) {}
