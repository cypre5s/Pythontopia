package com.pythontopia.learner.profile.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record CognitiveStyleDto(
    @JsonAlias("field_dependent") Double fieldDependent,
    @JsonAlias("sequential_vs_holistic") Double sequentialVsHolistic) {}
