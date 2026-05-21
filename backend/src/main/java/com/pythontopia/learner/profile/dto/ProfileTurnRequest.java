package com.pythontopia.learner.profile.dto;

import jakarta.validation.constraints.NotBlank;

public record ProfileTurnRequest(@NotBlank String userMessage) {}
