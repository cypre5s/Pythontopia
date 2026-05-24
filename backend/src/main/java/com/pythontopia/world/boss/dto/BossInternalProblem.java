package com.pythontopia.world.boss.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * R-13 §5 Phase 6 续：graph /agents/boss/problem 完整响应。
 *
 * <p>与 {@link BossProblemDto} 的区别：本 record 额外携带 {@code testCases} +
 * {@code referenceSolution}，仅在 backend 内部使用（缓存 / 转发给 graph evaluate），
 * 永远 <b>不</b> 序列化返给前端，避免 hidden test 答案泄露。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BossInternalProblem(
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
    @JsonProperty("aiGenerated") boolean aiGenerated,
    @JsonProperty("testCases") List<BossTestCasePayload> testCases,
    @JsonProperty("referenceSolution") String referenceSolution) {

  public BossInternalProblem {
    if (hints == null) {
      hints = List.of();
    }
    if (testCases == null) {
      testCases = List.of();
    }
    if (referenceSolution == null) {
      referenceSolution = "";
    }
  }

  /** 剥掉 hidden 字段，转成可返给前端的 {@link BossProblemDto}。 */
  public BossProblemDto toPublic() {
    return new BossProblemDto(
        problemId, chapter, stage, difficulty, kcCode,
        statement, hints, sampleInput, sampleOutput,
        totalTestCases, reviewed, aiGenerated);
  }
}
