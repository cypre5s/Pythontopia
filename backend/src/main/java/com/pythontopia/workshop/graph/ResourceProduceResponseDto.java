package com.pythontopia.workshop.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record ResourceProduceResponseDto(
    ResourceDraftDto artifact,
    String type,
    @JsonProperty("evidence_page_ids") List<Long> evidencePageIds,
    @JsonProperty("grounding_result") GroundingResultDto groundingResult,
    @JsonProperty("safety_result") SafetyResultDto safetyResult,
    @JsonProperty("graph_trace") List<GraphTraceStepDto> graphTrace,
    String status) {

  public record ResourceDraftDto(
      String title,
      String summary,
      @JsonProperty("content_format") String contentFormat,
      JsonNode handout,
      JsonNode mindmap,
      JsonNode practice,
      @JsonProperty("code_lab") JsonNode codeLab,
      JsonNode reading,
      JsonNode storyboard) {

    public JsonNode contentPayload() {
      if (handout != null) return handout;
      if (mindmap != null) return mindmap;
      if (practice != null) return practice;
      if (codeLab != null) return codeLab;
      if (reading != null) return reading;
      return storyboard;
    }
  }

  public record GroundingResultDto(
      Boolean passed,
      List<String> reasons,
      @JsonProperty("no_evidence_found") Boolean noEvidenceFound,
      Boolean retried) {}

  public record SafetyResultDto(Boolean passed, List<String> reasons) {}

  public record GraphTraceStepDto(
      String node, @JsonProperty("elapsed_ms") int elapsedMs, String status, String note) {}
}
