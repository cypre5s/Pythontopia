package com.pythontopia.learner.path.dto;

import java.util.List;

public record PlanPathResponse(
    LearningPathDto path,
    List<Long> archivedPathIds,
    int generatedQuestCount,
    String runStatus,
    String groundingMessage) {}
