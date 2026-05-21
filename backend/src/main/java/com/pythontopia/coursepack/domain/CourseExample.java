package com.pythontopia.coursepack.domain;

public record CourseExample(
    Long id,
    Long packId,
    String kcCode,
    String title,
    String code,
    String explanationMarkdown,
    Long[] pageRefs) {}
