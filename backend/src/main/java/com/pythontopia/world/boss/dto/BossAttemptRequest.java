package com.pythontopia.world.boss.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record BossAttemptRequest(
    @NotBlank @Size(max = 256) @JsonProperty("problemId") String problemId,
    @Min(1) @Max(7) @JsonProperty("chapter") int chapter,
    @Min(1) @Max(4) @JsonProperty("stage") int stage,
    @Size(max = 8192) @JsonProperty("code") String code,
    @Min(0) @JsonProperty("elapsedSec") int elapsedSec,
    @NotNull @JsonProperty("attemptId") UUID attemptId,
    @Size(max = 4) @JsonProperty("answers") List<@NotNull @Size(max = 512) String> answers) {

  public BossAttemptRequest(
      String problemId, int chapter, int stage, String code, int elapsedSec, UUID attemptId) {
    this(problemId, chapter, stage, code, elapsedSec, attemptId, null);
  }

  @AssertTrue(message = "code 或 answers 至少提供一项")
  @JsonIgnore
  public boolean isSubmissionPresent() {
    return (code != null && !code.isBlank()) || answers != null;
  }
}
