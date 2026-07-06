package com.pythontopia.learner.path.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record PathPlannerGraphResponse(
    @JsonProperty("user_id") Long userId,
    @JsonProperty("pack_id") Long packId,
    @JsonProperty("goal_summary") String goalSummary,
    @JsonProperty("nodes") List<NodeDraft> nodes,
    @JsonProperty("safety_result") SafetyResult safetyResult,
    @JsonProperty("grounding_result") GroundingResult groundingResult,
    @JsonProperty("deterministic_signature") String deterministicSignature,
    @JsonProperty("status") String status) {

  public record NodeDraft(
      @JsonProperty("day_index") int dayIndex,
      @JsonProperty("order_in_day") int orderInDay,
      @JsonProperty("kc_code") String kcCode,
      @JsonProperty("node_type") String nodeType,
      @JsonProperty("reason_summary") String reasonSummary,
      @JsonProperty("evidence_summary") String evidenceSummary,
      @JsonProperty("suggested_resource_type") String suggestedResourceType,
      @JsonProperty("suggested_problem_id") Long suggestedProblemId,
      @JsonProperty("source_classroom_quest_id") Long sourceClassroomQuestId) {}

  public record SafetyResult(
      @JsonProperty("passed") boolean passed, @JsonProperty("reasons") List<String> reasons) {}

  public record GroundingResult(
      @JsonProperty("passed") boolean passed,
      @JsonProperty("reasons") List<String> reasons,
      @JsonProperty("unknown_kc") List<String> unknownKc) {}
}
