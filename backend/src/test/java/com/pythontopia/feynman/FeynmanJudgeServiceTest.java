package com.pythontopia.feynman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.aop.framework.ProxyFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
  private final PlatformTransactionManager transactionManager = new TestTransactionManager();
  private FeynmanJudgeService service;

  @BeforeEach
  void runWorkerTasksSynchronously() {
    FeynmanJudgeService target =
        new FeynmanJudgeService(
            jdbc,
            sandbox,
            sessionService,
            masteryEvaluator,
            publisher,
            problemRepository,
            caseEvaluator,
            judgeWorkerPool,
            transactionManager);
    ProxyFactory proxyFactory = new ProxyFactory(target);
    proxyFactory.addAdvice(
        new TransactionInterceptor(
            transactionManager, new AnnotationTransactionAttributeSource()));
    service = (FeynmanJudgeService) proxyFactory.getProxy();
    when(judgeWorkerPool.submitAndWait(any()))
        .thenAnswer(
            invocation -> {
              Callable<?> task = invocation.getArgument(0);
              return task.call();
            });
  }

  @Test
  void sandboxWaitDoesNotHoldTransactionButVerdictWriteUsesOne() {
    givenSession("TAUGHT", 101L);
    givenProducedCode("print(4242)");
    givenHiddenCase("2\n", SECRET_EXPECTED);
    AtomicBoolean transactionDuringSandbox = new AtomicBoolean(true);
    AtomicBoolean transactionDuringVerdictWrite = new AtomicBoolean(false);
    when(sandbox.run(eq("print(4242)"), any(JudgeTestCase.class)))
        .thenAnswer(
            invocation -> {
              transactionDuringSandbox.set(
                  TransactionSynchronizationManager.isActualTransactionActive());
              return new SandboxRunResult(false, 0, SECRET_EXPECTED + "\n", "", 8, null);
            });
    when(jdbc.update(PASS_CLAIM_SQL, 10L))
        .thenAnswer(
            invocation -> {
              transactionDuringVerdictWrite.set(
                  TransactionSynchronizationManager.isActualTransactionActive());
              return 1;
            });

    Verdict verdict = service.judge(7L, 10L);

    assertThat(verdict.status()).isEqualTo("PASSED");
    assertThat(transactionDuringSandbox).isFalse();
    assertThat(transactionDuringVerdictWrite).isTrue();
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
  void noMistakeHistoryCannotRunMistakeSpecificJudgeOrUpdateMastery() {
    givenSession("OPEN", 101L, "{\"misconceptions\":[]}");

    Verdict verdict = service.judge(7L, 10L);

    assertThat(verdict.status()).isEqualTo("NA");
    assertThat(verdict.message()).contains("没有真实错题记录");
    verifyNoInteractions(masteryEvaluator, publisher, problemRepository);
    verify(jdbc, never()).query(eq(LATEST_CODE_SQL), any(RowMapper.class), eq(10L));
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
    String sentinel = "private-feynman-judge-error-sentinel";
    doThrow(new IllegalStateException(sentinel)).when(judgeWorkerPool).submitAndWait(any());

    Logger logger = (Logger) LoggerFactory.getLogger(FeynmanJudgeService.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    Verdict verdict;
    try {
      verdict = service.judge(7L, 10L);
    } finally {
      logger.detachAppender(appender);
    }

    assertThat(verdict.status()).isEqualTo("ERROR");
    assertThat(verdict.message()).doesNotContain(sentinel);
    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anySatisfy(message -> assertThat(message).contains("errorType=IllegalStateException"))
        .noneMatch(message -> message.contains(sentinel));
    assertThat(appender.list).allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
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
    givenSession(
        status,
        problemId,
        "{\"misconceptions\":[{\"tag\":\"test\",\"evidenceEventIds\":[501],\"resolved\":false}]}");
    when(sessionService.hasMisconceptions(
            "{\"misconceptions\":[{\"tag\":\"test\",\"evidenceEventIds\":[501],\"resolved\":false}]}"))
        .thenReturn(true);
  }

  private void givenSession(String status, Long problemId, String tuteeState) {
    when(sessionService.loadSession(7L, 10L))
        .thenReturn(
            new FeynmanSessionService.SessionData(
                10L, 7L, "PY43-KC-010", problemId, status, tuteeState));
    when(sessionService.hasMisconceptions(tuteeState)).thenReturn(false);
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

  @Test
  void sampleFallbackFailureIsNotDescribedAsHiddenCase() {
    givenSession("TAUGHT", 101L);
    givenProducedCode("print(1)");
    givenPublicCase("2\n", SECRET_EXPECTED);
    givenFailureClaim();
    when(sandbox.name()).thenReturn("subprocess");
    when(sandbox.run(eq("print(1)"), any(JudgeTestCase.class)))
        .thenReturn(new SandboxRunResult(false, 0, "1\n", "", 8, null));

    Verdict verdict = service.judge(7L, 10L);

    // 兜底跑的是公开样例，文案却拼在「未通过代表题隐藏用例」下 = 与事实不符。
    assertThat(verdict.message()).contains("公开样例").doesNotContain("隐藏用例");
  }

  @Test
  void alreadyPassedSessionReplaysVerdictWithoutScoringAgain() {
    givenSession("JUDGED_PASS", 101L);

    Verdict verdict = service.judge(7L, 10L);

    assertThat(verdict.passed()).isTrue();
    assertThat(verdict.status()).isEqualTo("PASSED");
    assertThat(verdict.message()).contains("重复判题不再计分");
    // 幂等：不再加掌握度、不再发 TaughtAiSucceeded、不再跑沙箱。
    verifyNoInteractions(masteryEvaluator);
    verifyNoInteractions(publisher);
    verifyNoInteractions(sandbox);
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void passingJudgeScoresExactlyOnce() {
    givenSession("TAUGHT", 101L);
    givenProducedCode("print(4242)");
    givenHiddenCase("2\n", SECRET_EXPECTED);
    when(jdbc.update(PASS_CLAIM_SQL, 10L)).thenReturn(1);
    when(sandbox.name()).thenReturn("subprocess");
    when(sandbox.run(eq("print(4242)"), any(JudgeTestCase.class)))
        .thenReturn(new SandboxRunResult(false, 0, SECRET_EXPECTED + "\n", "", 8, null));

    Verdict first = service.judge(7L, 10L);
    // 第二次判题时会话已是 JUDGED_PASS（真实库里由上一次 UPDATE 落盘）。
    givenSession("JUDGED_PASS", 101L);
    Verdict second = service.judge(7L, 10L);

    assertThat(first.passed()).isTrue();
    assertThat(second.passed()).isTrue();
    verify(masteryEvaluator, org.mockito.Mockito.times(1))
        .applyEvent(eq(7L), eq("PY43-KC-010"), any(), eq(BigDecimal.valueOf(0.10)), eq("feynman"));
    verify(publisher, org.mockito.Mockito.times(1))
        .publishEvent(any(LearningEvent.TaughtAiSucceeded.class));
  }

  @Test
  void sandboxSystemErrorIsReportedAsJudgeErrorNotStudentFailure() {
    givenSession("TAUGHT", 101L);
    givenProducedCode("print(1)");
    givenHiddenCase("2\n", SECRET_EXPECTED);
    when(sandbox.name()).thenReturn("subprocess");
    when(sandbox.run(eq("print(1)"), any(JudgeTestCase.class)))
        .thenReturn(new SandboxRunResult(false, 1, "", "boom", 8, "SYSTEM_ERROR"));

    Verdict verdict = service.judge(7L, 10L);

    assertThat(verdict.status()).isEqualTo("ERROR");
    assertThat(verdict.passed()).isFalse();
    assertThat(verdict.message()).doesNotContain("未通过");
    // 判题系统故障绝不归罪学生：不发 TaughtAiFailed、不写 JUDGED_FAIL。
    verify(publisher, never()).publishEvent(any(LearningEvent.TaughtAiFailed.class));
    verify(jdbc, never()).update(eq("UPDATE feynman_session SET status = 'JUDGED_FAIL' WHERE id = ?"), eq(10L));
  }

  @Test
  void missingTestCasesIsReportedAsJudgeErrorNotStudentFailure() {
    givenSession("TAUGHT", 101L);
    givenProducedCode("print(1)");
    when(problemRepository.findTestCases(101L)).thenReturn(Optional.empty());

    Verdict verdict = service.judge(7L, 10L);

    assertThat(verdict.status()).isEqualTo("ERROR");
    verify(publisher, never()).publishEvent(any(LearningEvent.TaughtAiFailed.class));
    verify(jdbc, never()).update(eq("UPDATE feynman_session SET status = 'JUDGED_FAIL' WHERE id = ?"), eq(10L));
  }

  @Test
  void functionDefinitionInProducedCodeIsNotTreatedAsAnswerLeak() {
    givenSession("TAUGHT", 101L);
    // 第 6/7 章的 KC（函数与变量作用域 / 文件操作）必然产出 def/import 开头的完整程序：
    // 用教学文本那套防作弊规则会把正确解判成「疑似直接泄答」。
    String code = "import sys\n\n\ndef solve():\n    print(4242)\n\n\nsolve()\n";
    givenProducedCode(code);
    givenHiddenCase("2\n", SECRET_EXPECTED);
    when(sandbox.name()).thenReturn("subprocess");
    when(sandbox.run(eq(code), any(JudgeTestCase.class)))
        .thenReturn(new SandboxRunResult(false, 0, SECRET_EXPECTED + "\n", "", 8, null));

    Verdict verdict = service.judge(7L, 10L);

    assertThat(verdict.status()).isEqualTo("PASSED");
    assertThat(verdict.message()).doesNotContain("泄答");
  }

  @Test
  void fencedProducedCodeIsUnwrappedBeforeSandbox() {
    givenSession("TAUGHT", 101L);
    givenProducedCode("```python\nprint(4242)\n```");
    givenHiddenCase("2\n", SECRET_EXPECTED);
    when(sandbox.name()).thenReturn("subprocess");
    when(sandbox.run(eq("print(4242)\n"), any(JudgeTestCase.class)))
        .thenReturn(new SandboxRunResult(false, 0, SECRET_EXPECTED + "\n", "", 8, null));

    Verdict verdict = service.judge(7L, 10L);

    assertThat(verdict.status()).isEqualTo("PASSED");
    verify(sandbox).run(eq("print(4242)\n"), any(JudgeTestCase.class));
  }

  @Test
  void answerKeyInProducedCodeIsStillRejected() {
    givenSession("TAUGHT", 101L);
    givenProducedCode("print(ANSWER_KEY)");
    givenFailureClaim();

    Verdict verdict = service.judge(7L, 10L);

    assertThat(verdict.status()).isEqualTo("FAILED");
    assertThat(verdict.message()).contains("answer_key");
    verify(publisher).publishEvent(any(LearningEvent.TaughtAiFailed.class));
  }

  @Test
  void conceptModeReturnsNeutralNotApplicableStatusInsteadOfFailure() {
    givenSession("OPEN", null);

    Verdict verdict = service.judge(7L, 10L);

    // 「本模式不做判题」是信息性说明，映射成 FAILED 会被渲染成红色失败卡片。
    assertThat(verdict.status()).isEqualTo("NA");
    assertThat(verdict.passed()).isFalse();
    verifyNoInteractions(publisher);
    verifyNoInteractions(masteryEvaluator);
  }

  private static final class TestTransactionManager extends AbstractPlatformTransactionManager {

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(
        Object transaction, org.springframework.transaction.TransactionDefinition definition) {}

    @Override
    protected void doCommit(DefaultTransactionStatus status) {}

    @Override
    protected void doRollback(DefaultTransactionStatus status) {}
  }
}
