package com.pythontopia.coursepack.domain;

public record CourseChapter(Long id, Long packId, Integer orderIndex, String title, String summaryMarkdown) {}
