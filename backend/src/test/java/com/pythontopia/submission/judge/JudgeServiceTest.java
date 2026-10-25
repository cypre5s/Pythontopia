package com.pythontopia.submission.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.submission.dto.SubmissionStatus;
import com.pythontopia.submission.dto.Verdict;
import com.pythontopia.submission.judge.JudgeService.JudgeOutcome;
import com.pythontopia.submission.repository.PracticeSubmissionRepository;
import com.pythontopia.submission.repository.PublicProblemRepository;
import com.pythontopia.submission.service.SubmissionTransactionService;
import com.pythontopia.learner.quest.service.QuestService;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class JudgeServiceTest {

  private final PracticeSubmissionRepository submissions = mock(PracticeSubmissionRepository.class);
  private final PublicProblemRepository problems = mock(PublicProblemRepository.class);
  private final SubmissionTransactionService submissionTransactions =
      new SubmissionTransactionService(
          submissions,
          problems,
          mock(QuestService.class),
          mock(ApplicationEventPublisher.class));
  private final PythonSandbox sandbox = mock(PythonSandbox.class);
  private final StaticCodeChecker checker = new StaticCodeChecker("python3");
  private final ObjectMapper mapper = new ObjectMapper();
  private final JudgeWorkerPool workerPool = new JudgeWorkerPool();
  private final JudgeCaseEvaluator caseEvaluator = new JudgeCaseEvaluator(sandbox, mapper);
  private JudgeService judge;

  @BeforeEach
  void setUp() {
    judge =
        new JudgeService(
            submissionTransactions, problems, sandbox, checker, mapper, workerPool, caseEvaluator);
  }

  @Test
  void ceWhenStaticCheckFails() {
    JudgeOutcome outcome = judge.judge(1L, 2L, "import os\nprint(1)", 7L);
    assertThat(outcome.verdict()).isEqualTo(Verdict.CE);
    verify(submissions).markStatus(eq(1L), eq(SubmissionStatus.RUNNING), eq(null), eq(null));
    verify(submissions).markStatus(eq(1L), eq(SubmissionStatus.FAILED), eq(0), eq(null));
  }

  @Test
  void acWhenAllCasesPass() {
    setupTestCases("1\n", "1");
    when(sandbox.name()).thenReturn("subprocess");
    when(sandbox.run(any(), any())).thenReturn(new SandboxRunResult(false, 0, "1\n", "", 12, null));
    JudgeOutcome outcome = judge.judge(1L, 2L, "print(input())", 7L);
    assertThat(outcome.verdict()).isEqualTo(Verdict.AC);
    assertThat(outcome.passedCases()).isEqualTo(1);
    assertThat(outcome.totalCases()).isEqualTo(1);
    verify(submissions).markStatus(eq(1L), eq(SubmissionStatus.PASSED), anyInt(), eq(null));
  }

  @Test
  void persistsMeasuredPeakMemory() {
    setupTestCases("1\n", "1");
    when(sandbox.name()).thenReturn("piston");
    when(sandbox.run(any(), any()))
        .thenReturn(new SandboxRunResult(false, 0, "1\n", "", 12, 24, null));

    judge.judge(1L, 2L, "print(input())", 7L);

    verify(submissions).markStatus(eq(1L), eq(SubmissionStatus.PASSED), eq(12), eq(24));
  }

  @Test
  void waWhenOutputDiffers() {
    setupTestCases("1\n", "2");
    when(sandbox.name()).thenReturn("subprocess");
    when(sandbox.run(any(), any())).thenReturn(new SandboxRunResult(false, 0, "1\n", "", 10, null));
    JudgeOutcome outcome = judge.judge(1L, 2L, "print(input())", 7L);
    assertThat(outcome.verdict()).isEqualTo(Verdict.WA);
    verify(submissions).markStatus(eq(1L), eq(SubmissionStatus.FAILED), anyInt(), eq(null));
  }

  @Test
  void tleWhenSandboxTimesOut() {
    setupTestCases("1\n", "1");
    when(sandbox.name()).thenReturn("subprocess");
    when(sandbox.run(any(), any())).thenReturn(new SandboxRunResult(true, -1, "", "", 5000, "TLE"));
    JudgeOutcome outcome = judge.judge(1L, 2L, "while True:\n  pass", 7L);
    assertThat(outcome.verdict()).isEqualTo(Verdict.TLE);
  }

  @Test
  void reWhenExitCodeNonZero() {
    setupTestCases("1\n", "1");
    when(sandbox.name()).thenReturn("subprocess");
    when(sandbox.run(any(), any())).thenReturn(new SandboxRunResult(false, 1, "", "Traceback", 8, null));
    JudgeOutcome outcome = judge.judge(1L, 2L, "raise Exception()", 7L);
    assertThat(outcome.verdict()).isEqualTo(Verdict.RE);
  }

  @Test
  void systemErrorWhenNoTestCasesAvailable() {
    when(problems.findTestCases(eq(2L), eq(7L))).thenReturn(Optional.empty());
    JudgeOutcome outcome = judge.judge(1L, 2L, "print(1)", 7L);
    assertThat(outcome.verdict()).isEqualTo(Verdict.SYSTEM_ERROR);
    verify(submissions).markStatus(eq(1L), eq(SubmissionStatus.ERROR), eq(0), eq(null));
  }

  /**
   * 回归：静态检查报基础设施故障（解释器缺 ast / 无法 fork）时，必须判 SYSTEM_ERROR + ERROR，
   * 绝不能把判题环境问题记成学生的 CE + FAILED（否则会扣掌握度、往画像写脏 error_pattern）。
   */
  @Test
  void systemErrorWhenStaticCheckerHitsInfrastructureFailure() {
    // 指向一个不存在的解释器 → runAstWhitelist 抛 IOException → CheckResult.systemError()=true
    StaticCodeChecker brokenChecker =
        new StaticCodeChecker("/nonexistent/python-that-cannot-be-launched");
    JudgeService brokenJudge =
        new JudgeService(
            submissionTransactions, problems, sandbox, brokenChecker, mapper, workerPool, caseEvaluator);

    JudgeOutcome outcome = brokenJudge.judge(1L, 2L, "print(1)", 7L);

    assertThat(outcome.verdict()).isEqualTo(Verdict.SYSTEM_ERROR);
    assertThat(outcome.failureReason()).startsWith("SYSTEM_ERROR:");
    verify(submissions)
        .insertJudgeResult(eq(1L), eq(Verdict.SYSTEM_ERROR), eq(0), eq(0), any(), any());
    verify(submissions).markStatus(eq(1L), eq(SubmissionStatus.ERROR), eq(0), eq(null));
  }

  @Test
  void runsAstStaticCheckOnBoundedJudgeWorkerPool() {
    AtomicReference<String> checkThread = new AtomicReference<>();
    StaticCodeChecker threadRecordingChecker =
        new StaticCodeChecker("python3") {
          @Override
          public CheckResult check(String code) {
            checkThread.set(Thread.currentThread().getName());
            return super.check(code);
          }
        };
    JudgeService checkedJudge =
        new JudgeService(
            submissionTransactions, problems, sandbox, threadRecordingChecker, mapper, workerPool, caseEvaluator);

    checkedJudge.judge(1L, 2L, "print(1)", 7L);

    assertThat(checkThread.get()).startsWith("judge-worker-");
  }

  /** 回归：评测阶段的 SYSTEM_ERROR（沙箱 IOException 等）必须落 ERROR，而不是学生失败 FAILED。 */
  @Test
  void evaluationSystemErrorMarksStatusErrorNotFailed() {
    setupTestCases("1\n", "1");
    when(sandbox.name()).thenReturn("subprocess");
    when(sandbox.run(any(), any()))
        .thenReturn(new SandboxRunResult(false, -1, "", "Sandbox error", 0L, "SYSTEM_ERROR"));

    JudgeOutcome outcome = judge.judge(1L, 2L, "print(1)", 7L);

    assertThat(outcome.verdict()).isEqualTo(Verdict.SYSTEM_ERROR);
    verify(submissions).markStatus(eq(1L), eq(SubmissionStatus.ERROR), anyInt(), eq(null));
  }

  /** 回归：隐藏用例的期望输出不得随 failureReason 回传给学生（硬约束：不允许输出隐藏测试）。 */
  @Test
  void hiddenCaseFailureNeverLeaksExpectedOutput() {
    com.fasterxml.jackson.databind.node.ArrayNode hidden = mapper.createArrayNode();
    hidden.add(mapper.createObjectNode().put("input", "1\n").put("output", "SECRET-42"));
    when(problems.findTestCases(eq(2L), eq(7L)))
        .thenReturn(
            Optional.of(
                new PublicProblemRepository.JudgeTestCases(mapper.createArrayNode(), hidden)));
    when(sandbox.name()).thenReturn("subprocess");
    when(sandbox.run(any(), any())).thenReturn(new SandboxRunResult(false, 0, "0\n", "", 5, null));

    JudgeOutcome outcome = judge.judge(1L, 2L, "print(0)", 7L);

    assertThat(outcome.verdict()).isEqualTo(Verdict.WA);
    assertThat(outcome.failureReason()).doesNotContain("SECRET-42");
    assertThat(outcome.failureReason()).isEqualTo("第 1 个隐藏用例输出不匹配");
  }

  private void setupTestCases(String input, String output) {
    com.fasterxml.jackson.databind.node.ArrayNode samples = mapper.createArrayNode();
    samples.add(mapper.createObjectNode().put("input", input).put("output", output));
    PublicProblemRepository.JudgeTestCases tc =
        new PublicProblemRepository.JudgeTestCases(samples, mapper.createArrayNode());
    when(problems.findTestCases(eq(2L), eq(7L))).thenReturn(Optional.of(tc));
  }
}
