package com.pythontopia.learner.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record LearningEventLogDto(
    @NotBlank @Size(max = 64) String eventType,
    Map<String, Object> payload
) {}
