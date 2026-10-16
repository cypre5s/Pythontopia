package com.pythontopia.rag.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 课件 RAG 检索请求：{@code query} 必填，可按 {@code kc_codes} / {@code chapter_ids} 缩小范围，
 * {@code min_score} 为相关度下限；缺省时 {@code topK} 取 8、{@code minScore} 取 0.3。
 * 字段 snake_case 对齐 pythontopia-rag。
 */
public record RagQueryRequestDto(
    @NotBlank @Size(max = 512) String query,
    @Min(1) @Max(20) @JsonProperty("top_k") int topK,
    @Size(max = 65) @JsonProperty("kc_codes") List<@Size(max = 32) String> kcCodes,
    @Size(max = 7) @JsonProperty("chapter_ids") List<Long> chapterIds,
    @JsonProperty("min_score") Double minScore,
    @Pattern(regexp = "off|auto|force") @JsonProperty("graph_mode") String graphMode,
    @Min(1) @Max(2) @JsonProperty("prereq_hops") int prereqHops,
    @JsonProperty("expand_prerequisites") Boolean expandPrerequisites) {

  public RagQueryRequestDto {
    if (topK <= 0) {
      topK = 8;
    }
    if (minScore == null) {
      minScore = 0.3d;
    }
    if (prereqHops <= 0) {
      prereqHops = 1;
    }
    if (expandPrerequisites == null) {
      expandPrerequisites = true;
    }
  }

  public RagQueryRequestDto(
      String query, int topK, List<String> kcCodes, List<Long> chapterIds, Double minScore) {
    this(query, topK, kcCodes, chapterIds, minScore, null, 1, true);
  }
}
