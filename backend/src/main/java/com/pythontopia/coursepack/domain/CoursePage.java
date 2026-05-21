package com.pythontopia.coursepack.domain;

public record CoursePage(
    Long id,
    Long packId,
    Long documentId,
    Long chapterId,
    Integer pageNo,
    String title,
    String contentMarkdown,
    String excerpt) {}
