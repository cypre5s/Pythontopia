package com.pythontopia.world.boss.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * R-13 §5 Phase 6：Boss 题目 DTO，与前端 {@code BossProblemDto} 一一对应。
 *
 * <p>graph 服务返回的 JSON 字段为 {@code camelCase}（见 schemas.BossProblem.to_payload），
 * 这里同样用 camelCase，无需 @JsonProperty 转换。
 */
public record BossProblemDto(
    @JsonProperty("problemId") String problemId,
    @JsonProperty("chapter") int chapter,
    @JsonProperty("stage") int stage,
    @JsonProperty("difficulty") int difficulty,
    @JsonProperty("kcCode") String kcCode,
    @JsonProperty("statement") String statement,
    @JsonProperty("hints") List<String> hints,
    @JsonProperty("sampleInput") String sampleInput,
    @JsonProperty("sampleOutput") String sampleOutput,
    @JsonProperty("totalTestCases") int totalTestCases,
    @JsonProperty("reviewed") boolean reviewed,
    @JsonProperty("aiGenerated") boolean aiGenerated) {

  public BossProblemDto {
    if (hints == null) {
      hints = List.of();
    }
  }
}
