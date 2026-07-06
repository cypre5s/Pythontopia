package com.pythontopia.workshop.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.List;

public record LearningResourceDto(
    Long id,
    Long userId,
    Long packId,
    List<String> kcCodes,
    Long chapterId,
    ResourceType type,
    String title,
    ContentFormat contentFormat,
    JsonNode content,
    List<Long> evidencePageIds,
    String summary,
    ResourceStatusFlag safetyStatus,
    ResourceStatusFlag groundingStatus,
    ResourceFeedbackStatus feedbackStatus,
    String feedbackNote,
    OffsetDateTime createdAt) {}
