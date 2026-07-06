package com.pythontopia.workshop.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ResourceListItemDto(
    Long id,
    ResourceType type,
    String title,
    String summary,
    List<String> kcCodes,
    ResourceStatusFlag groundingStatus,
    ResourceFeedbackStatus feedbackStatus,
    OffsetDateTime createdAt) {}
