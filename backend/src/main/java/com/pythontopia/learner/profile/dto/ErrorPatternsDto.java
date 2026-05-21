package com.pythontopia.learner.profile.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;

public record ErrorPatternsDto(
    @JsonAlias("frequent_kc_codes") List<String> frequentKcCodes,
    @JsonAlias("common_categories") List<String> commonCategories) {}
