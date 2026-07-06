package com.pythontopia.media;

/** 媒体异步合成作业生命周期，对应 {@code media_job.status} CHECK 约束（V158）。 */
public enum MediaJobStatus {
  PENDING,
  RUNNING,
  SUCCESS,
  FAILED
}
