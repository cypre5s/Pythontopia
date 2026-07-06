package com.pythontopia.bugmine.dto;

import jakarta.validation.constraints.NotBlank;

public record BugMineEchoOpenRequest(@NotBlank String kcCode) {}
