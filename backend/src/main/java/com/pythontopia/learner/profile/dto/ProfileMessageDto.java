package com.pythontopia.learner.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProfileMessageDto(
    @NotBlank @Pattern(regexp = "user|assistant") String role,
    @NotBlank @Size(max = 2000) String content) {}
