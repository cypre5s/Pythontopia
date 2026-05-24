package com.pythontopia.world.boss.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 前端请求 boss 题目时 body。
 *
 * <p>stage 1..4；chapter 由 path variable 提供，不在 body。
 */
public record BossProblemRequest(
    @Min(1) @Max(4) @JsonProperty("stage") int stage,
    @JsonProperty("kcCode") String kcCode,
    @JsonProperty("difficulty") Integer difficulty) {

  public BossProblemRequest {
    if (kcCode != null && kcCode.isBlank()) {
      kcCode = null;
    }
  }
}
