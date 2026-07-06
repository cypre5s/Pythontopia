package com.pythontopia.media.dto;

import com.pythontopia.media.MediaJobService;

/**
 * {@code POST /api/media/storyboard/video/jobs} 的 {@code 202} 响应。命中幂等缓存时
 * {@code cached=true,status=SUCCESS,assetId} 直给成片；否则 {@code status=PENDING,cached=false}，
 * 客户端再用 {@code GET /api/media/jobs/{id}} 或 SSE 跟踪进度。
 */
public record JobSubmitResponse(long jobId, String status, boolean cached, Long assetId) {

  public static JobSubmitResponse from(MediaJobService.SubmitResult result) {
    return new JobSubmitResponse(
        result.jobId(), result.status().name(), result.cached(), result.assetId());
  }
}
