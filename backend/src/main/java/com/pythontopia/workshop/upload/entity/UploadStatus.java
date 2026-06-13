package com.pythontopia.workshop.upload.entity;

/** 上传文件生命周期：受理中 / 解析成功可用 / 解析失败。与 workshop_upload.status CHECK 约束对齐。 */
public enum UploadStatus {
  PENDING,
  READY,
  FAILED
}
