package com.pythontopia.classroom.domain;

public record Classroom(
    Long id,
    String name,
    String description,
    Long teacherId,
    Long packId,
    String joinCode,
    String status) {}
