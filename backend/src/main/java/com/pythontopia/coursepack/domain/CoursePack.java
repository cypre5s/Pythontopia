package com.pythontopia.coursepack.domain;

public record CoursePack(
    Long id,
    String slug,
    Integer version,
    String name,
    String primaryLanguage,
    String description,
    String status,
    Integer documentCount,
    Integer pageCount,
    Integer chapterCount,
    Integer kcCount,
    Integer exampleCount,
    Integer problemCount) {}
