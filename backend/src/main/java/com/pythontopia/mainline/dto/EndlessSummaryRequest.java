package com.pythontopia.mainline.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 无尽一轮小结上报（POST /api/mainline/endless/summary，§7/§9）：触发 ENDLESS_RECOVERY 补星判定。
 *
 * <p><b>止血校验</b>：correct/total/streak 目前全部来自客户端自证（服务端无无尽轮次落库，无法交叉核对），
 * 因此这里做最小可信性约束：
 *
 * <ul>
 *   <li>{@code correct <= total}（跨字段）——否则 {@code 8/1 = 8.0} 这类构造能直接刷到 100% 正确率；
 *   <li>单轮题量/答对数/连对数上限 {@link #MAX_ROUND_QUESTIONS}——挡住「一轮做了 10 万题」的离谱上报。
 * </ul>
 *
 * <p>服务端 {@code MainlineRecoveryService.recordEndlessSummary} 内另有同口径兜底（不依赖 Bean Validation）。
 */
public record EndlessSummaryRequest(
    @NotNull @Min(1) @Max(7) Integer chapter,
    @NotNull @Min(0) @Max(MAX_ROUND_QUESTIONS) Integer correct,
    @NotNull @Min(1) @Max(MAX_ROUND_QUESTIONS) Integer total,
    @NotNull @Min(0) @Max(MAX_ROUND_QUESTIONS) Integer streak) {

  /** 单轮无尽题量的合理上限（防离谱自证；无尽一轮远达不到此量级）。 */
  public static final long MAX_ROUND_QUESTIONS = 200L;

  /** 跨字段校验：答对数不可超过总题数。 */
  @AssertTrue(message = "correct 不能大于 total")
  public boolean isCorrectNotAboveTotal() {
    return correct == null || total == null || correct <= total;
  }

  /** 跨字段校验：连对数不可超过总题数。 */
  @AssertTrue(message = "streak 不能大于 total")
  public boolean isStreakNotAboveTotal() {
    return streak == null || total == null || streak <= total;
  }
}
