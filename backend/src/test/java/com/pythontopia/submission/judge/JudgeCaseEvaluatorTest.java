package com.pythontopia.submission.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.submission.dto.Verdict;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 判题结论映射回归：进程被信号杀死（128+signal）不是学生的运行时错误，
 * 隐藏用例的期望输出不得出现在学生可见的 failureReason 中。
 */
class JudgeCaseEvaluatorTest {

  private final PythonSandbox sandbox = mock(PythonSandbox.class);
  private final ObjectMapper mapper = new ObjectMapper();
  private final JudgeCaseEvaluator evaluator = new JudgeCaseEvaluator(sandbox, mapper);

  private JudgeCaseEvaluator.CaseEvaluation evaluate(
      SandboxRunResult result, JudgeTestCase testCase) {
    when(sandbox.name()).thenReturn("subprocess");
    when(sandbox.run(any(), any())).thenReturn(result);
    return evaluator.evaluate("print(1)", List.of(testCase));
  }

  @Test
  void exitCode137IsTimeoutNotRuntimeError() {
    // ulimit -t / OOM killer 用 SIGKILL 杀掉进程 → 128+9；exec 替换了 bash，stderr 为空。
    JudgeCaseEvaluator.CaseEvaluation evaluation =
        evaluate(
            new SandboxRunResult(false, 137, "", "", 5001, null),
            new JudgeTestCase("", "1", 5000));

    assertThat(evaluation.finalVerdict()).isEqualTo(Verdict.TLE);
    assertThat(evaluation.failureReason()).isEqualTo("超过时间限制 5000ms");
  }

  @Test
  void exitCode152SigxcpuIsTimeoutNotRuntimeError() {
    JudgeCaseEvaluator.CaseEvaluation evaluation =
        evaluate(
            new SandboxRunResult(false, 152, "", "", 5001, null),
            new JudgeTestCase("", "1", 5000));

    assertThat(evaluation.finalVerdict()).isEqualTo(Verdict.TLE);
  }

  @Test
  void memoryErrorIsMleNotRuntimeError() {
    JudgeCaseEvaluator.CaseEvaluation evaluation =
        evaluate(
            new SandboxRunResult(
                false, 1, "", "Traceback (most recent call last):\nMemoryError\n", 120, null),
            new JudgeTestCase("", "1", 5000));

    assertThat(evaluation.finalVerdict()).isEqualTo(Verdict.MLE);
    assertThat(evaluation.failureReason()).isEqualTo("超出内存限制");
  }

  @Test
  void plainNonZeroExitStaysRuntimeError() {
    JudgeCaseEvaluator.CaseEvaluation evaluation =
        evaluate(
            new SandboxRunResult(false, 1, "", "ZeroDivisionError", 9, null),
            new JudgeTestCase("", "1", 5000));

    assertThat(evaluation.finalVerdict()).isEqualTo(Verdict.RE);
  }

  @Test
  void hiddenCaseWrongAnswerHidesExpectedOutput() {
    JudgeCaseEvaluator.CaseEvaluation evaluation =
        evaluate(
            new SandboxRunResult(false, 0, "0\n", "", 7, null),
            new JudgeTestCase("in", "SECRET-EXPECTED", 5000, true));

    assertThat(evaluation.finalVerdict()).isEqualTo(Verdict.WA);
    assertThat(evaluation.failureReason()).doesNotContain("SECRET-EXPECTED");
    assertThat(evaluation.failureReason()).isEqualTo("第 1 个隐藏用例输出不匹配");
  }

  @Test
  void visibleSampleCaseStillShowsExpectedOutput() {
    JudgeCaseEvaluator.CaseEvaluation evaluation =
        evaluate(
            new SandboxRunResult(false, 0, "0\n", "", 7, null),
            new JudgeTestCase("in", "42", 5000, false));

    assertThat(evaluation.finalVerdict()).isEqualTo(Verdict.WA);
    assertThat(evaluation.failureReason()).contains("42");
  }

  @Test
  void extractCasesMarksHiddenFlag() {
    com.fasterxml.jackson.databind.node.ArrayNode array = mapper.createArrayNode();
    array.add(mapper.createObjectNode().put("input", "1").put("output", "2"));

    assertThat(evaluator.extractCases(array, 10, true).get(0).hidden()).isTrue();
    assertThat(evaluator.extractCases(array, 10).get(0).hidden()).isFalse();
  }
}
