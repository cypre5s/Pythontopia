package com.pythontopia.learner.profile.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KcIndexItemDto(
    @JsonProperty("kc_code") String kcCode, @JsonProperty("kc_name") String kcName) {}
