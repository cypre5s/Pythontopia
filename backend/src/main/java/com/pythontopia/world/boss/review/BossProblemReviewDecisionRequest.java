package com.pythontopia.world.boss.review;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BossProblemReviewDecisionRequest(
    @NotNull BossProblemReviewDecision decision,
    @NotBlank @Size(max = 1000) String reason,
    @Min(1) int version) {

  public BossProblemReviewDecisionRequest {
    reason = reason == null ? null : reason.trim();
  }
}
