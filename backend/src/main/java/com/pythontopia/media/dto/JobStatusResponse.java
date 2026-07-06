package com.pythontopia.media.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.media.domain.MediaJob;

/**
 * {@code GET /api/media/jobs/{id}} 的轮询兜底响应。{@code progress} 为 {@code media_job.progress_json}
 * 原貌（{@code {phase,sceneIndex,sceneTotal,roleKey,message,percent}}）；成功时 {@code url} 指向成片，
 * 失败时 {@code errorCode} 给出失败码。
 */
public record JobStatusResponse(
    long jobId, String status, JsonNode progress, Long assetId, String url, String errorCode) {

  public static JobStatusResponse from(MediaJob job, ObjectMapper objectMapper) {
    JsonNode progress;
    try {
      String raw = job.progressJson();
      progress =
          (raw == null || raw.isBlank())
              ? objectMapper.createObjectNode()
              : objectMapper.readTree(raw);
    } catch (Exception ex) {
      progress = objectMapper.createObjectNode();
    }
    String url = job.assetId() == null ? null : "/api/media/assets/" + job.assetId();
    return new JobStatusResponse(
        job.id(), job.status().name(), progress, job.assetId(), url, job.errorCode());
  }
}
