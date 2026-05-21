package com.pythontopia.bugmine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BugMineRetryRequest(@NotBlank @Size(max = 8192) String code) {}
