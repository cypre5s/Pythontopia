package com.pythontopia.classroom.quest.dto;

public record ClassroomQuestPublishResponse(
    ClassroomQuestDto classroomQuest, int totalStudents, int createdQuests, int skippedDuplicates) {}
