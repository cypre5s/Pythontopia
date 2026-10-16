package com.pythontopia.world.boss.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * graph {@code /agents/boss/problem} 完整响应。
 *
 * <p>与 {@link BossProblemDto} 的区别：额外携带 {@code testCases} 与 {@code referenceSolution}，
 * 仅 backend 内部使用（缓存 / 转发给 graph evaluate），永远 <b>不</b>序列化返给前端，
 * 避免 hidden test 答案泄露。
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
    @JsonProperty("referenceSolution") String referenceSolution,
    @JsonProperty("questionType") BossQuestionType questionType,
    @JsonProperty("options") List<String> options,
    @JsonProperty("answers") List<String> answers,
    @JsonProperty("blankCount") int blankCount) {

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
    if (questionType == null) questionType = BossQuestionType.CODE;
    if (options == null) options = List.of();
    if (answers == null) answers = List.of();
  }

  public BossInternalProblem(
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
      boolean aiGenerated,
      List<BossTestCasePayload> testCases,
      String referenceSolution) {
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
        testCases,
        referenceSolution,
        BossQuestionType.CODE,
        List.of(),
        List.of(),
        0);
  }

  public BossInternalProblem issueAs(String issuedProblemId, String authoritativeKcCode) {
    return new BossInternalProblem(
        issuedProblemId,
        chapter,
        stage,
        difficulty,
        authoritativeKcCode,
        statement,
        hints,
        sampleInput,
        sampleOutput,
        totalTestCases,
        reviewed,
        aiGenerated,
        testCases,
        referenceSolution,
        questionType,
        options,
        answers,
        blankCount);
  }

  /** 剥掉 hidden 字段，转成可返给前端的 {@link BossProblemDto}。 */
  public BossProblemDto toPublic() {
    return new BossProblemDto(
        problemId, chapter, stage, difficulty, kcCode,
        statement, hints, sampleInput, sampleOutput,
        totalTestCases, reviewed, aiGenerated, questionType, options, blankCount);
  }
}
