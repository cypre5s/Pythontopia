package com.pythontopia.workshop.upload.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pythontopia.workshop.upload.entity.UploadStatus;
import com.pythontopia.workshop.upload.entity.WorkshopUpload;

/**
 * 上传文件明细视图（{@code GET /api/workshop/uploads/{id}}）。在 {@link UploadDto} 字段基础上附带
 * {@code extractedText}——A 通路前端取它截断后以【你上传的《filename》】标签注入答疑 context。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UploadDetailDto(
    Long id,
    String filename,
    String ext,
    long byteSize,
    int charCount,
    int lineCount,
    UploadStatus status,
    String error,
    String extractedText) {

  public static UploadDetailDto from(WorkshopUpload upload) {
    return new UploadDetailDto(
        upload.id(),
        upload.filename(),
        upload.ext(),
        upload.byteSize(),
        upload.charCount(),
        upload.lineCount(),
        upload.status(),
        upload.error(),
        upload.extractedText());
  }
}
