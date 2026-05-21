package com.pythontopia.rag.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record RagQueryRequestDto(
    @NotBlank @Size(max = 512) String query,
    @Min(1) @Max(20) @JsonProperty("top_k") int topK,
    @JsonProperty("kc_codes") List<String> kcCodes,
    @JsonProperty("chapter_ids") List<Long> chapterIds,
    @JsonProperty("min_score") Double minScore) {

  public RagQueryRequestDto {
    if (topK <= 0) {
      topK = 8;
    }
    if (minScore == null) {
      minScore = 0.3d;
    }
  }
}
