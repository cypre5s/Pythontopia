package com.pythontopia.workshop.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResourceFeedbackRequest(
    @NotNull ResourceFeedbackStatus status,
    @Size(max = 512) String note) {}
