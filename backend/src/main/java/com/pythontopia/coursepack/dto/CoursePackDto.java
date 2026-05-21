package com.pythontopia.coursepack.dto;

public record CoursePackDto(
    Long packId,
    String name,
    String primaryLanguage,
    Integer documentCount,
    Integer pageCount,
    Integer chapterCount,
    Integer kcCount,
    Integer exampleCount,
    Integer problemCount) {}
