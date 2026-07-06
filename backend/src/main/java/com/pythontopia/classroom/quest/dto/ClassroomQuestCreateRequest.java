package com.pythontopia.classroom.quest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record ClassroomQuestCreateRequest(
    @NotBlank @Size(max = 256) String title,
    @Size(max = 1024) String description,
    @NotNull @Size(min = 1, max = 16) List<@NotBlank @Size(max = 64) String> kcCodes,
    @Positive Long targetProblemId,
    @Size(max = 16) String targetResourceType,
    @NotNull LocalDate dueDate) {}
