package com.pythontopia.coursepack.dto;

public record PageDto(
    Long pageId,
    Long documentId,
    Long chapterId,
    Integer pageNo,
    String title,
    String contentMarkdown,
    String excerpt) {}
