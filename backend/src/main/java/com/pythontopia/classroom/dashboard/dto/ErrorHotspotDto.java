package com.pythontopia.classroom.dashboard.dto;

import java.util.List;

public record ErrorHotspotDto(
    String kcCode,
    String kcName,
    int failedSubmissionCount,
    int affectedStudentCount,
    List<ErrorCategoryDto> categories) {

  public record ErrorCategoryDto(String category, int count) {}
}
