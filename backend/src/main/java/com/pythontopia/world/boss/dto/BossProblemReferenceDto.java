package com.pythontopia.world.boss.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Statement-only projection used by Graph's duplicate-question review. */
public record BossProblemReferenceDto(
    @JsonProperty("problemId") String problemId, @JsonProperty("statement") String statement) {}
