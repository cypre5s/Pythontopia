package com.pythontopia.learner.profile.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record PracticeCapabilityDto(
    @JsonAlias("avg_attempts_per_problem") Double avgAttemptsPerProblem,
    @JsonAlias("success_rate") Double successRate) {}
