package com.pythontopia.workshop.upload.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pythontopia.workshop.upload.entity.UploadStatus;
import com.pythontopia.workshop.upload.entity.WorkshopUpload;

/**
 * 上传文件摘要视图（列表 / 创建响应）。不含 {@code extractedText}（见 {@link UploadDetailDto}），
 * 避免列表拉取大文本。{@code error} 仅在解析失败时出现（{@code NON_NULL} 略去成功态的 null）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UploadDto(
    Long id,
    String filename,
    String ext,
    long byteSize,
    int charCount,
    int lineCount,
    UploadStatus status,
    String error) {

  public static UploadDto from(WorkshopUpload upload) {
    return new UploadDto(
        upload.id(),
        upload.filename(),
        upload.ext(),
        upload.byteSize(),
        upload.charCount(),
        upload.lineCount(),
        upload.status(),
        upload.error());
  }
}
