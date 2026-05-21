package com.pythontopia.classroom.dto;

public record ClassroomDto(
    Long classroomId,
    String name,
    String description,
    Long teacherId,
    Long packId,
    String status,
    String joinCode) {}
