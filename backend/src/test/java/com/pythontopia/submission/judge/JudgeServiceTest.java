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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JudgeServiceTest {

  private final PracticeSubmissionRepository submissions = mock(PracticeSubmissionRepository.class);
  private final PublicProblemRepository problems = mock(PublicProblemRepository.class);
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
            submissions, problems, sandbox, checker, mapper, workerPool, caseEvaluator);
  }

  @Test
  void ceWhenStaticCheckFails() {
    JudgeOutcome outcome = judge.judge(1L, 2L, "import os\nprint(1)");
    assertThat(outcome.verdict()).isEqualTo(Verdict.CE);
    verify(submissions).markStatus(eq(1L), eq(SubmissionStatus.RUNNING), eq(null), eq(null));
    verify(submissions).markStatus(eq(1L), eq(SubmissionStatus.FAILED), eq(0), eq(null));
  }

  @Test
  void acWhenAllCasesPass() {
    setupTestCases("1\n", "1");
    when(sandbox.name()).thenReturn("subprocess");
    when(sandbox.run(any(), any())).thenReturn(new SandboxRunResult(false, 0, "1\n", "", 12, null));
    JudgeOutcome outcome = judge.judge(1L, 2L, "print(input())");
    assertThat(outcome.verdict()).isEqualTo(Verdict.AC);
    assertThat(outcome.passedCases()).isEqualTo(1);
    assertThat(outcome.totalCases()).isEqualTo(1);
    verify(submissions).markStatus(eq(1L), eq(SubmissionStatus.PASSED), anyInt(), eq(null));
  }

  @Test
  void waWhenOutputDiffers() {
    setupTestCases("1\n", "2");
    when(sandbox.name()).thenReturn("subprocess");
    when(sandbox.run(any(), any())).thenReturn(new SandboxRunResult(false, 0, "1\n", "", 10, null));
    JudgeOutcome outcome = judge.judge(1L, 2L, "print(input())");
    assertThat(outcome.verdict()).isEqualTo(Verdict.WA);
    verify(submissions).markStatus(eq(1L), eq(SubmissionStatus.FAILED), anyInt(), eq(null));
  }

  @Test
  void tleWhenSandboxTimesOut() {
    setupTestCases("1\n", "1");
    when(sandbox.name()).thenReturn("subprocess");
    when(sandbox.run(any(), any())).thenReturn(new SandboxRunResult(true, -1, "", "", 5000, "TLE"));
    JudgeOutcome outcome = judge.judge(1L, 2L, "while True:\n  pass");
    assertThat(outcome.verdict()).isEqualTo(Verdict.TLE);
  }

  @Test
  void reWhenExitCodeNonZero() {
    setupTestCases("1\n", "1");
    when(sandbox.name()).thenReturn("subprocess");
    when(sandbox.run(any(), any())).thenReturn(new SandboxRunResult(false, 1, "", "Traceback", 8, null));
    JudgeOutcome outcome = judge.judge(1L, 2L, "raise Exception()");
    assertThat(outcome.verdict()).isEqualTo(Verdict.RE);
  }

  @Test
  void systemErrorWhenNoTestCasesAvailable() {
    when(problems.findTestCases(eq(2L))).thenReturn(Optional.empty());
    JudgeOutcome outcome = judge.judge(1L, 2L, "print(1)");
    assertThat(outcome.verdict()).isEqualTo(Verdict.SYSTEM_ERROR);
  }

  private void setupTestCases(String input, String output) {
    com.fasterxml.jackson.databind.node.ArrayNode samples = mapper.createArrayNode();
    samples.add(mapper.createObjectNode().put("input", input).put("output", output));
    PublicProblemRepository.JudgeTestCases tc =
        new PublicProblemRepository.JudgeTestCases(samples, mapper.createArrayNode());
    when(problems.findTestCases(eq(2L))).thenReturn(Optional.of(tc));
  }
}
