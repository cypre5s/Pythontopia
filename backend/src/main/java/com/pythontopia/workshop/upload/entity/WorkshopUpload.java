package com.pythontopia.workshop.upload.entity;

import java.time.OffsetDateTime;

/**
 * {@code workshop_upload} 行的领域表示。原始文件不落盘，仅保留解析后的 {@code extractedText} 与计数。
 * 列名/语义见 V162 迁移；{@code error} 仅在 {@link UploadStatus#FAILED} 时非空。
 */
public record WorkshopUpload(
    Long id,
    Long userId,
    String filename,
    String ext,
    long byteSize,
    String extractedText,
    int charCount,
    int lineCount,
    UploadStatus status,
    String error,
    OffsetDateTime createdAt) {}
