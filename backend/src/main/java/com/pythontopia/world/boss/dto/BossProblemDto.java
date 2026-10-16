package com.pythontopia.world.boss.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Boss 题目 DTO，与前端 {@code BossProblemDto} 一一对应。
 *
 * <p>graph 返回 JSON 字段为 {@code camelCase}（见 schemas.BossProblem.to_payload），与此处字段名一致，
 * {@code @JsonProperty} 仅显式标注、无重命名。
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
    @JsonProperty("aiGenerated") boolean aiGenerated,
    @JsonProperty("questionType") BossQuestionType questionType,
    @JsonProperty("options") List<String> options,
    @JsonProperty("blankCount") int blankCount) {

  public BossProblemDto {
    if (hints == null) {
      hints = List.of();
    }
    if (questionType == null) questionType = BossQuestionType.CODE;
    if (options == null) options = List.of();
  }

  public BossProblemDto(
      String problemId,
      int chapter,
      int stage,
      int difficulty,
      String kcCode,
      String statement,
      List<String> hints,
      String sampleInput,
      String sampleOutput,
      int totalTestCases,
      boolean reviewed,
      boolean aiGenerated) {
    this(
        problemId,
        chapter,
        stage,
        difficulty,
        kcCode,
        statement,
        hints,
        sampleInput,
        sampleOutput,
        totalTestCases,
        reviewed,
        aiGenerated,
        BossQuestionType.CODE,
        List.of(),
        0);
  }
}
