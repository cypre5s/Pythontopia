package com.pythontopia.learner.profile.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/** Admin supplied replacement payload for a quarantined profile projection. */
public record ProfileDimensionProjectionRepairRequest(
    @NotBlank
        @Pattern(
            regexp =
                "^(professional_background|knowledge_base|learning_goal|cognitive_style|learning_preference|error_pattern|practical_ability|emotion_persistence)$")
        String dimensionKey,
    @NotNull @Valid Delta delta) {

  public DimensionDelta toDimensionDelta() {
    return delta.toDimensionDelta();
  }

  public record Delta(
      @DecimalMin("-1.0") @DecimalMax("1.0") Double scoreDelta,
      @Min(0) @Max(1000) Integer evidenceIncrement,
      @Size(max = 16) List<@NotBlank @Size(max = 64) String> tagsAdd,
      @Size(max = 32) Map<@NotBlank @Size(max = 64) String, Object> metadata,
      @NotBlank
          @Pattern(
              regexp =
                  "^(INTERVIEW|SUBMISSION|DEBUG|RESOURCE_FEEDBACK|QUEST|CLASSROOM|EVALUATION|PRACTICE_PASS|PRACTICE_FAIL|DEBUG_FIX|REFLECTION)$")
          String source) {

    @AssertTrue(message = "delta must contain a score, evidence, tag or metadata change")
    public boolean isEffective() {
      return (scoreDelta != null && scoreDelta != 0.0)
          || (evidenceIncrement != null && evidenceIncrement > 0)
          || (tagsAdd != null && !tagsAdd.isEmpty())
          || (metadata != null && !metadata.isEmpty());
    }

    @AssertTrue(message = "scoreDelta must be finite")
    public boolean isFiniteScoreDelta() {
      return scoreDelta == null || Double.isFinite(scoreDelta);
    }

    DimensionDelta toDimensionDelta() {
      return new DimensionDelta(scoreDelta, evidenceIncrement, tagsAdd, metadata, source);
    }
  }
}
