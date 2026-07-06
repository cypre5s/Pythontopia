package com.pythontopia.workshop.upload.parser;

/**
 * 文件内容解析失败（PDF/DOCX 损坏、加密、格式非法等）。由 service 捕获并落
 * {@code status=FAILED + error}，绝不冒泡成 500——上传解析失败是预期可恢复态，不是服务器错误。
 */
public class UploadParseException extends RuntimeException {

  public UploadParseException(String message) {
    super(message);
  }
}
