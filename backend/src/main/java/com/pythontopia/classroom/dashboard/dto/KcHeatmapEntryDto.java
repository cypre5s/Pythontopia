package com.pythontopia.classroom.dashboard.dto;

import java.math.BigDecimal;

public record KcHeatmapEntryDto(
    String kcCode,
    String kcName,
    Long chapterId,
    Integer chapterOrder,
    BigDecimal avgMastery,
    int studentCount,
    int masteredCount,
    int learningCount,
    int strugglingCount) {}
