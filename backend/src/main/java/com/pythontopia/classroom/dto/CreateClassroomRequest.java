package com.pythontopia.classroom.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateClassroomRequest(
    @NotBlank @Size(max = 128) String name, @Size(max = 1000) String description) {}
