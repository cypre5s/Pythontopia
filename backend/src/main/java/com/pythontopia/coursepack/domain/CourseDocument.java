package com.pythontopia.coursepack.domain;

public record CourseDocument(
    Long id,
    Long packId,
    String originalFilename,
    String canonicalPath,
    String previewPdfPath,
    Integer pageCount,
    String status,
    Integer sortOrder) {}
