package com.pythontopia.learner.evaluation.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record EvaluationNarrativeResponse(
    @JsonProperty("narrative_md") String narrativeMd,
    @JsonProperty("key_recommendations") List<String> keyRecommendations,
    @JsonProperty("path_diff_explainer") String pathDiffExplainer,
    @JsonProperty("safety_result") SafetyResult safetyResult,
    @JsonProperty("grounding_result") GroundingResult groundingResult,
    @JsonProperty("deterministic_signature") String deterministicSignature,
    String status) {

  public record SafetyResult(boolean passed, List<String> reasons) {}

  public record GroundingResult(
      boolean passed,
      List<String> reasons,
      @JsonProperty("unknown_numbers") List<String> unknownNumbers,
      @JsonProperty("unknown_kc") List<String> unknownKc) {}
}
