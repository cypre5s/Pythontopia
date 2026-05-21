package com.pythontopia.coursepack.domain;

public record CourseKc(
    Long id,
    Long packId,
    String kcCode,
    String kcName,
    Long chapterId,
    Integer chapterOrder,
    String difficulty,
    String summaryMarkdown) {}
