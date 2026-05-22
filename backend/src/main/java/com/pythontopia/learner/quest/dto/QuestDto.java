package com.pythontopia.learner.quest.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

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
    String requiredEvidenceType,
    BigDecimal requiredMasteryDelta,
    List<Long> targetProblemIds,
    Long targetResourceId,
    String deepLinkRoute,
    JsonNode deepLinkParams,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
