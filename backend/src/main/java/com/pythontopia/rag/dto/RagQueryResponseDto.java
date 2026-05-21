package com.pythontopia.rag.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record RagQueryResponseDto(
    List<RagHitDto> hits,
    @JsonProperty("no_evidence_found") boolean noEvidenceFound,
    @JsonProperty("rewritten_query") String rewrittenQuery) {

  public List<RagHitDto> hits() {
    return hits == null ? List.of() : hits;
  }
}
