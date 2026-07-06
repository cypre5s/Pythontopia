package com.pythontopia.coursepack.domain;

public record CourseProblem(
    Long id,
    Long packId,
    String kcCode,
    String title,
    String statementMarkdown,
    String inputDescription,
    String outputDescription,
    String difficulty,
    String sourceLabel) {}
