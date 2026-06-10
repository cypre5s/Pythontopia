package com.pythontopia.mainline.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 开考下发的<b>去答案</b>题面（{@code POST .../test/start} 响应，§4.2）。
 *
 * <p>不含 answer/answers/explain/hidden_test_cases/reference_solution/evidence_page_ids——解析仅在提交后返回。
 */
public record ChapterTestPaperDto(
    long testId,
    int chapter,
    int attemptNo,
    String status,
    int timeLimitSec,
    OffsetDateTime deadlineAt,
    List<QuestionView> items) {

  /** 去答案题面单题。 */
  public record QuestionView(
      String qid,
      String type,
      int score,
      String stem,
      String title,
      String statementMarkdown,
      List<String> options,
      List<SampleView> samples) {}

  public record SampleView(String input, String output) {}
}
