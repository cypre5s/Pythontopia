package com.pythontopia.classroom.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record JoinClassroomRequest(@NotBlank @Pattern(regexp = "^[A-Z0-9]{6}$") String joinCode) {}
