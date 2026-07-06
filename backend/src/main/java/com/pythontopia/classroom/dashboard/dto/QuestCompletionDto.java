package com.pythontopia.classroom.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record QuestCompletionDto(
    Long classroomQuestId,
    String title,
    LocalDate dueDate,
    String status,
    int totalStudents,
    int completedCount,
    int inProgressCount,
    int skippedCount,
    BigDecimal completionRate) {}
