package com.pythontopia.coursepack.dto;

public record ProblemDto(
    Long problemId,
    String kcCode,
    String title,
    String statementMarkdown,
    String inputDescription,
    String outputDescription,
    String difficulty,
    String sourceLabel) {}
