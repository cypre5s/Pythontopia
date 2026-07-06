package com.pythontopia.mainline.test.question;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * 章测单题完整内部模型（含答案/解析/证据页/隐藏用例，§3 题面 JSON）。生成端=graph，判分端=backend。
 * 绝不整体下发前端：由 ChapterTestPaperMapper 去答案投影（§4.2）。字段按题型可空（CHOICE/JUDGE/FILL/CODING）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QuestionItem(
    String qid,
    QuestionType type,
    String stem,
    String title,
    @JsonProperty("statement_markdown") String statementMarkdown,
    List<String> options,
    JsonNode answer,
    List<String> answers,
    String match,
    List<Sample> samples,
    @JsonProperty("hidden_test_cases") JsonNode hiddenTestCases,
    @JsonProperty("reference_solution") String referenceSolution,
    @JsonProperty("kc_code") String kcCode,
    @JsonProperty("evidence_page_ids") List<Long> evidencePageIds,
    String explain,
    int score) {

  /** CODING 样例（公开 input/output）。 */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Sample(String input, String output) {}

  public List<String> safeOptions() {
    return options == null ? List.of() : options;
  }

  public List<String> safeAnswers() {
    return answers == null ? List.of() : answers;
  }

  public List<Sample> safeSamples() {
    return samples == null ? List.of() : samples;
  }
}
