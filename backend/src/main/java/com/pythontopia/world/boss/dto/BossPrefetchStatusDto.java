package com.pythontopia.world.boss.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Observable state of one Boss/Endless preload dimension. */
public record BossPrefetchStatusDto(
    @JsonProperty("jobId") Long jobId,
    @JsonProperty("status") String status,
    @JsonProperty("attempts") int attempts,
    @JsonProperty("errorMessage") String errorMessage,
    @JsonProperty("retryable") boolean retryable,
    @JsonProperty("updatedAt") String updatedAt) {

  public static BossPrefetchStatusDto idle() {
    return new BossPrefetchStatusDto(null, "IDLE", 0, null, true, null);
  }
}
