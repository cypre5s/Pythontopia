package com.pythontopia.mainline.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 章测结果（{@code POST .../submit} 与 {@code GET .../latest} 响应，§9）。
 *
 * <p>仅在终态（SUBMITTED/EXPIRED）下回填逐题判分明细 + 正确答案 + 解析；非终态（如 latest 命中 IN_PROGRESS）
 * 不泄漏答案（items 为空）。
 */
public record ChapterTestResultDto(
    long testId,
    int chapter,
    int attemptNo,
    String status,
    Integer score,
    Integer maxScore,
    boolean passed,
    int stars,
    boolean overtime,
    OffsetDateTime submittedAt,
    List<QuestionResultView> items,
    IntegrityView integrity) {

  /** 逐题判分（提交后才下发，含正确答案与解析）。 */
  public record QuestionResultView(
      String qid,
      String type,
      boolean correct,
      int awardedScore,
      int maxScore,
      String yourAnswer,
      String correctAnswer,
      String explain,
      Integer passedCases,
      Integer totalCases) {}

  public record IntegrityView(int pasteBlocked, int blurEvents, boolean overtime) {}
}
