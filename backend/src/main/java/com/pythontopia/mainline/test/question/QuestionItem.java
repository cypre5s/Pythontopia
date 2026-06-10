package com.pythontopia.mainline.test.question;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * 章测单题<b>完整内部模型</b>（含答案/解析/证据页/隐藏用例），对齐 r15 计划 §3 题面 JSON 契约。
 *
 * <p>生成端 = pythontopia-graph；判分端 = backend（本类）。<b>绝不</b>整体序列化给前端：下发题面时由
 * {@code ChapterTestPaperMapper} 去答案投影（§4.2「下发去答案题面」）。
 *
 * <p>字段按题型可空：
 *
 * <ul>
 *   <li>CHOICE：{@code stem} / {@code options} / {@code answer}(选项标识，如 "B") / {@code evidencePageIds}
 *   <li>JUDGE：{@code stem} / {@code answer}(布尔)
 *   <li>FILL：{@code stem} / {@code answers}(候选集) / {@code match}("normalized")
 *   <li>CODING：{@code title} / {@code statementMarkdown} / {@code samples} / {@code hiddenTestCases} /
 *       {@code referenceSolution}
 * </ul>
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
