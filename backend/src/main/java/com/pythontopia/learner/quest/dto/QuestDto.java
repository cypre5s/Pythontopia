package com.pythontopia.learner.quest.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record QuestDto(
    Long id,
    Long userId,
    QuestSource source,
    Long sourceRefId,
    String title,
    String description,
    String kcCode,
    QuestNodeType nodeType,
    JsonNode payload,
    LocalDate dueDate,
    int priority,
    QuestStatus status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
