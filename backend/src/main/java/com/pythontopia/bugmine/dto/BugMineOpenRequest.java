package com.pythontopia.bugmine.dto;

import jakarta.validation.constraints.NotNull;

public record BugMineOpenRequest(@NotNull Long submissionId) {}
