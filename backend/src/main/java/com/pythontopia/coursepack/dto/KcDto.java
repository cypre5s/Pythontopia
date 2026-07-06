package com.pythontopia.coursepack.dto;

public record KcDto(
    Long kcId,
    String kcCode,
    String kcName,
    Long chapterId,
    Integer chapterOrder,
    String difficulty,
    String summaryMarkdown) {}
