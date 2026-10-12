package com.pythontopia.world.boss.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record BossProblemReviewCreateRequest(
    @Min(1) @Max(7) int chapter,
    @Min(1) @Max(4) int stage,
    @Size(max = 64) String kcCode,
    @Min(1) @Max(5) Integer difficulty,
    @Size(max = 32) String kind) {

  public BossProblemReviewCreateRequest {
    kcCode = normalize(kcCode);
    kind = normalize(kind);
  }

  private static String normalize(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
