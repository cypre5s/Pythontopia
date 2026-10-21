package com.pythontopia.admin;

import java.util.List;

public record AdminCourseResourcePageDto(
    List<AdminCourseResourceDto> content,
    int page,
    int size,
    long totalElements,
    int totalPages) {}
