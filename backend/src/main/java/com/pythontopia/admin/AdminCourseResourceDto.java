package com.pythontopia.admin;

public record AdminCourseResourceDto(
    String resourceType,
    long resourceId,
    String title,
    String category,
    String status,
    boolean enabled,
    String preview,
    Integer pageCount) {}
