package com.pythontopia.classroom.quest.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.pythontopia.learner.quest.dto.QuestNodeType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record ClassroomQuestDto(
    Long id,
    Long classroomId,
    String title,
    String description,
    List<String> kcCodes,
    QuestNodeType nodeType,
    JsonNode payload,
    LocalDate dueDate,
    Long createdBy,
    OffsetDateTime createdAt,
    String status,
    Long targetProblemId,
    String targetResourceType) {}
