package com.pythontopia.feynman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.pythontopia.feynman.dto.FeynmanDtos.Verdict;
import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.learner.mastery.MasteryEvaluator;
import com.pythontopia.submission.judge.JudgeCaseEvaluator;
import com.pythontopia.submission.judge.JudgeTestCase;
import com.pythontopia.submission.judge.JudgeWorkerPool;
import com.pythontopia.submission.judge.PythonSandbox;
import com.pythontopia.submission.judge.SandboxRunResult;
import com.pythontopia.submission.repository.PublicProblemRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@SuppressWarnings("unchecked")
class FeynmanJudgeServiceTest {

  private static final String LATEST_CODE_SQL =
      "SELECT taught_fix FROM feynman_turn "
          + "WHERE session_id = ? AND taught_fix IS NOT NULL "
          + "ORDER BY created_at DESC LIMIT 1";
  private static final String PASS_CLAIM_SQL =
      "UPDATE feynman_session SET status = 'JUDGED_PASS' "
          + "WHERE id = ? AND status <> 'JUDGED_PASS'";
  private static final String FAIL_CLAIM_SQL =
      "UPDATE feynman_session SET status = 'JUDGED_FAIL' "
          + "WHERE id = ? AND status <> 'JUDGED_PASS'";
  private static final String SECRET_EXPECTED = "4242";

  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final PythonSandbox sandbox = mock(PythonSandbox.class);
  private final FeynmanSessionService sessionService = mock(FeynmanSessionService.class);
  private final MasteryEvaluator masteryEvaluator = mock(MasteryEvaluator.class);
  private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
  private final PublicProblemRepository problemRepository = mock(PublicProblemRepository.class);
  private final JudgeWorkerPool judgeWorkerPool = mock(JudgeWorkerPool.class);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final JudgeCaseEvaluator caseEvaluator = new JudgeCaseEvaluator(sandbox, objectMapper);

  private final FeynmanJudgeService service =
      new FeynmanJudgeService(
          jdbc,
          sandbox,
          sessionService,
          masteryEvaluator,
          publisher,
          problemRepository,
          caseEvaluator,
          judgeWorkerPool);

  @BeforeEach
  void runWorkerTasksSynchronously() {
    when(judgeWorkerPool.submitAndWait(any()))
        .thenAnswer(
            invocation -> {
              Callable<?> task = invocation.getArgument(0);
              return task.call();
            });
  }

  @Test
  void alreadyPassedSessionReplaysSuccessWithoutRunningOrScoringAgain() {
    givenSession("JUDGED_PASS", 101L);

    Verdict verdict = service.judge(7L, 10L);

    assertThat(verdict.status()).isEqualTo("PASSED");
    assertThat(verdict.passed()).isTrue();
    assertThat(verdict.message()).contains("重复判题不再计分");
    verifyNoInteractions(sandbox, masteryEvaluator, publisher, problemRepository);
    verify(judgeWorkerPool, never()).submitAndWait(any());
  }

  @Test
  void conceptModeReturnsNeutralNotApplicableStatus() {
    givenSession("OPEN", null);

    Verdict verdict = service.judge(7L, 10L);

    assertThat(verdict.status()).isEqualTo("NA");
    assertThat(verdict.passed()).isFalse();
    verifyNoInteractions(masteryEvaluator, publisher, problemRepository);
    verify(judgeWorkerPool, never()).submitAndWait(any());
  }

  @Test
  void missingTestCasesIsJudgeErrorNotStudentFailure() {
    givenSession("TAUGHT", 101L);
    givenProducedCode("print(1)");
    when(problemRepository.findTestCases(101L)).thenReturn(Optional.empty());

    Verdict verdict = service.judge(7L, 10L);

    assertThat(verdict.status()).isEqualTo("ERROR");
    verify(publisher, never()).publishEvent(any(LearningEvent.TaughtAiFailed.class));
    verify(jdbc, never()).update(eq(FAIL_CLAIM_SQL), eq(10L));
    verify(judgeWorkerPool, never()).submitAndWait(any());
  }

  @Test
  void sandboxSystemErrorIsJudgeErrorNotStudentFailure() {
    givenSession("TAUGHT", 101L);
    givenProducedCode("print(1)");
    givenHiddenCase("2\n", SECRET_EXPECTED);
    when(sandbox.run(eq("print(1)"), any(JudgeTestCase.class)))
        .thenReturn(new SandboxRunResult(false, 1, "", "boom", 8, "SYSTEM_ERROR"));

    Verdict verdict = service.judge(7L, 10L);

    assertThat(verdict.status()).isEqualTo("ERROR");
    assertThat(verdict.message()).doesNotContain("未通过");
    verify(publisher, never()).publishEvent(any(LearningEvent.TaughtAiFailed.class));
    verify(jdbc, never()).update(eq(FAIL_CLAIM_SQL), eq(10L));
  }

  @Test
  void workerFailureIsJudgeErrorNotStudentFailure() {
    givenSession("TAUGHT", 101L);
    givenProducedCode("print(1)");
    givenHiddenCase("2\n", SECRET_EXPECTED);
    doThrow(new IllegalStateException("pool down")).when(judgeWorkerPool).submitAndWait(any());

    Verdict verdict = service.judge(7L, 10L);

    assertThat(verdict.status()).isEqualTo("ERROR");
    assertThat(verdict.message()).doesNotContain("pool down");
    verify(publisher, never()).publishEvent(any(LearningEvent.TaughtAiFailed.class));
    verify(jdbc, never()).update(eq(FAIL_CLAIM_SQL), eq(10L));
  }

  @Test
  void hiddenFailureDoesNotExposeExpectedOrActualOutput() {
    givenSession("TAUGHT", 101L);
    givenProducedCode("print(1)");
    givenHiddenCase("2\n", SECRET_EXPECTED);
    givenFailureClaim();
    when(sandbox.run(eq("print(1)"), any(JudgeTestCase.class)))
        .thenReturn(new SandboxRunResult(false, 0, "1\n", "", 8, null));

    Verdict verdict = service.judge(7L, 10L);

    assertThat(verdict.status()).isEqualTo("FAILED");
    assertThat(verdict.message())
        .contains("隐藏用例")
        .doesNotContain("期望", "实际", SECRET_EXPECTED, "1\\n");
    verify(publisher).publishEvent(any(LearningEvent.TaughtAiFailed.class));
  }

  @Test
  void publicSampleFailureIsLabelledHonestlyWithoutExposingExpectedOutput() {
    givenSession("TAUGHT", 101L);
    givenProducedCode("print(1)");
    givenPublicCase("2\n", SECRET_EXPECTED);
    givenFailureClaim();
    when(sandbox.run(eq("print(1)"), any(JudgeTestCase.class)))
        .thenReturn(new SandboxRunResult(false, 0, "1\n", "", 8, null));

    Verdict verdict = service.judge(7L, 10L);

    assertThat(verdict.status()).isEqualTo("FAILED");
    assertThat(verdict.message())
        .contains("公开样例")
        .doesNotContain("隐藏用例", "期望", "实际", SECRET_EXPECTED);
  }

  @Test
  void fencedFunctionCodeReachesSandboxAndFirstPassScoresExactlyOnce() {
    givenSession("TAUGHT", 101L);
    givenProducedCode("```python\ndef solve():\n    print(4242)\nsolve()\n```");
    givenHiddenCase("2\n", SECRET_EXPECTED);
    when(jdbc.update(PASS_CLAIM_SQL, 10L)).thenReturn(1);
    String executableCode = "def solve():\n    print(4242)\nsolve()\n";
    when(sandbox.run(eq(executableCode), any(JudgeTestCase.class)))
        .thenReturn(new SandboxRunResult(false, 0, SECRET_EXPECTED + "\n", "", 8, null));

    Verdict verdict = service.judge(7L, 10L);

    assertThat(verdict.status()).isEqualTo("PASSED");
    assertThat(verdict.passed()).isTrue();
    verify(sandbox).run(eq(executableCode), any(JudgeTestCase.class));
    verify(judgeWorkerPool).submitAndWait(any());
    verify(masteryEvaluator)
        .applyEvent(
            eq(7L),
            eq("PY43-KC-010"),
            any(),
            eq(BigDecimal.valueOf(0.10)),
            eq("feynman"));
    verify(publisher).publishEvent(any(LearningEvent.TaughtAiSucceeded.class));
  }

  @Test
  void concurrentPassingReplayDoesNotApplyMasteryOrPublishAgain() {
    givenSession("TAUGHT", 101L);
    givenProducedCode("print(4242)");
    givenHiddenCase("2\n", SECRET_EXPECTED);
    when(jdbc.update(PASS_CLAIM_SQL, 10L)).thenReturn(0);
    when(sandbox.run(eq("print(4242)"), any(JudgeTestCase.class)))
        .thenReturn(new SandboxRunResult(false, 0, SECRET_EXPECTED + "\n", "", 8, null));

    Verdict verdict = service.judge(7L, 10L);

    assertThat(verdict.status()).isEqualTo("PASSED");
    assertThat(verdict.message()).contains("重复判题不再计分");
    verifyNoInteractions(masteryEvaluator, publisher);
  }

  @Test
  void failedClaimCannotOverwriteAConcurrentPass() {
    givenSession("TAUGHT", 101L);
    givenProducedCode("print(1)");
    givenHiddenCase("2\n", SECRET_EXPECTED);
    when(jdbc.update(FAIL_CLAIM_SQL, 10L)).thenReturn(0);
    when(sandbox.run(eq("print(1)"), any(JudgeTestCase.class)))
        .thenReturn(new SandboxRunResult(false, 0, "1\n", "", 8, null));

    Verdict verdict = service.judge(7L, 10L);

    assertThat(verdict.status()).isEqualTo("PASSED");
    verify(publisher, never()).publishEvent(any(LearningEvent.TaughtAiFailed.class));
  }

  @Test
  void answerKeyMarkerInProducedCodeIsStillRejected() {
    givenSession("TAUGHT", 101L);
    givenProducedCode("print(ANSWER_KEY)");
    givenFailureClaim();

    Verdict verdict = service.judge(7L, 10L);

    assertThat(verdict.status()).isEqualTo("FAILED");
    assertThat(verdict.message()).containsIgnoringCase("answer_key");
    verify(judgeWorkerPool, never()).submitAndWait(any());
  }

  private void givenSession(String status, Long problemId) {
    when(sessionService.loadSession(7L, 10L))
        .thenReturn(
            new FeynmanSessionService.SessionData(
                10L, 7L, "PY43-KC-010", problemId, status, "{}"));
  }

  private void givenProducedCode(String code) {
    when(jdbc.query(eq(LATEST_CODE_SQL), any(RowMapper.class), eq(10L)))
        .thenReturn(List.of(code));
  }

  private void givenHiddenCase(String input, String expected) {
    ArrayNode hidden = objectMapper.createArrayNode();
    hidden.add(objectMapper.createObjectNode().put("input", input).put("output", expected));
    when(problemRepository.findTestCases(101L))
        .thenReturn(
            Optional.of(
                new PublicProblemRepository.JudgeTestCases(
                    objectMapper.createArrayNode(), hidden)));
  }

  private void givenPublicCase(String input, String expected) {
    ArrayNode samples = objectMapper.createArrayNode();
    samples.add(objectMapper.createObjectNode().put("input", input).put("output", expected));
    when(problemRepository.findTestCases(101L))
        .thenReturn(
            Optional.of(
                new PublicProblemRepository.JudgeTestCases(
                    samples, objectMapper.createArrayNode())));
  }

  private void givenFailureClaim() {
    when(jdbc.update(FAIL_CLAIM_SQL, 10L)).thenReturn(1);
  }
}
