package com.pythontopia.learner.profile.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;

public record BaseLevelDto(
    @JsonAlias("programming_years") Integer programmingYears,
    @JsonAlias("languages_known") List<String> languagesKnown,
    @JsonAlias("python_self_rated_level") Integer pythonSelfRatedLevel) {}
