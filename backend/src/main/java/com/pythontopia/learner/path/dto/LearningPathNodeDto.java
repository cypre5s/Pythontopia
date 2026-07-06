package com.pythontopia.learner.path.dto;

public record LearningPathNodeDto(
    Long id,
    Long pathId,
    int dayIndex,
    int orderInDay,
    String kcCode,
    PathNodeType nodeType,
    String reasonSummary,
    String evidenceSummary,
    SuggestedResourceType suggestedResourceType,
    Long suggestedProblemId,
    Long sourceClassroomQuestId) {}
