package com.pythontopia.submission.judge;

/**
 * 单个判题用例。
 *
 * @param hidden 是否为隐藏用例。隐藏用例的 {@code expectedOutput} 属于题目机密，
 *     <strong>禁止</strong>出现在任何回传学生的文本里（见
 *     {@code JudgeCaseEvaluator#summariseFailure}）。
 */
public record JudgeTestCase(
    String input, String expectedOutput, int timeoutMs, boolean hidden) {

  public JudgeTestCase {
    if (timeoutMs <= 0) {
      timeoutMs = 5000;
    }
    if (input == null) {
      input = "";
    }
    if (expectedOutput == null) {
      expectedOutput = "";
    }
  }

  /** 公开样例用例（{@code hidden=false}）。 */
  public JudgeTestCase(String input, String expectedOutput, int timeoutMs) {
    this(input, expectedOutput, timeoutMs, false);
  }
}
