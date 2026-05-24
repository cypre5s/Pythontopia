package com.pythontopia.world.boss.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BossAttemptResultDto(
    @JsonProperty("problemId") String problemId,
    @JsonProperty("passed") boolean passed,
    @JsonProperty("passedTestCases") int passedTestCases,
    @JsonProperty("totalTestCases") int totalTestCases,
    @JsonProperty("failureFirstHint") String failureFirstHint,
    @JsonProperty("score") int score,
    @JsonProperty("feedback") String feedback) {}
