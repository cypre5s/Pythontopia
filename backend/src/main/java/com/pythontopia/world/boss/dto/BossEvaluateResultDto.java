package com.pythontopia.world.boss.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * graph {@code /agents/boss/evaluate} 响应。
 *
 * <p>{@link com.pythontopia.world.boss.BossService} 拿到后转成对前端的 {@link BossAttemptResultDto}
 * （部分字段重命名，score 由 backend 重新计算）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BossEvaluateResultDto(
    @JsonProperty("problemId") String problemId,
    @JsonProperty("passed") boolean passed,
    @JsonProperty("passedTestCases") int passedTestCases,
    @JsonProperty("totalTestCases") int totalTestCases,
    @JsonProperty("failureFirstHint") String failureFirstHint,
    @JsonProperty("runtimeError") String runtimeError,
    @JsonProperty("failedIndices") List<Integer> failedIndices) {

  public BossEvaluateResultDto {
    if (failedIndices == null) {
      failedIndices = List.of();
    }
  }
}
