package com.pythontopia.feynman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

@SuppressWarnings("unchecked")
class FeynmanJudgeServiceTest {

  private static final String LATEST_CODE_SQL =
      "SELECT taught_fix FROM feynman_turn "
          + "WHERE session_id = ? AND taught_fix IS NOT NULL "
          + "ORDER BY created_at DESC LIMIT 1";

  /** 隐藏用例的期望输出：判罚文案里绝不允许出现它。 */
  private static final String SECRET_EXPECTED = "4242";

  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final PythonSandbox sandbox = mock(PythonSandbox.class);
  private final FeynmanSessionService sessionService = mock(FeynmanSessionService.class);
  private final MasteryEvaluator masteryEvaluator = mock(MasteryEvaluator.class);
  private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
  private final PublicProblemRepository problemRepository = mock(PublicProblemRepository.class);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final JudgeCaseEvaluator caseEvaluator = new JudgeCaseEvaluator(sandbox, objectMapper);
  private final JudgeWorkerPool judgeWorkerPool = new JudgeWorkerPool();

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

  private void givenSession(String status, Long problemId) {
    when(sessionService.loadSession(7L, 10L))
        .thenReturn(
            new FeynmanSessionService.SessionData(
                10L, 7L, "PY43-KC-010", problemId, status, "{}"));
  }

  private void givenProducedCode(String code) {
    when(jdbc.query(eq(LATEST_CODE_SQL), any(org.springframework.jdbc.core.RowMapper.class), eq(10L)))
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

  private void givenSampleCase(String input, String expected) {
    ArrayNode samples = objectMapper.createArrayNode();
    samples.add(objectMapper.createObjectNode().put("input", input).put("output", expected));
    when(problemRepository.findTestCases(101L))
        .thenReturn(
            Optional.of(
                new PublicProblemRepository.JudgeTestCases(
                    samples, objectMapper.createArrayNode())));
  }

  @Test
  void judgeFailsWhenProducedCodeDoesNotMatchRepresentativeHiddenCase() {
    givenSession("TAUGHT", 101L);
    givenProducedCode("print(1)");
    givenHiddenCase("2\n", SECRET_EXPECTED);
    when(sandbox.name()).thenReturn("subprocess");
    when(sandbox.run(eq("print(1)"), any(JudgeTestCase.class)))
        .thenReturn(new SandboxRunResult(false, 0, "1\n", "", 8, null));

    Verdict verdict = service.judge(7L, 10L);

    assertThat(verdict.passed()).isFalse();
    assertThat(verdict.status()).isEqualTo("FAILED");
    assertThat(verdict.message()).contains("隐藏用例");
    // 隐藏用例的期望输出与实际输出都绝不能回显给学生。
    assertThat(verdict.message()).doesNotContain("期望").doesNotContain("实际");
    assertThat(verdict.message()).doesNotContain(SECRET_EXPECTED);
    verify(jdbc).update("UPDATE feynman_session SET status = 'JUDGED_FAIL' WHERE id = ?", 10L);
  }

  @Test
  void sampleFallbackFailureIsNotDescribedAsHiddenCase() {
    givenSession("TAUGHT", 101L);
    givenProducedCode("print(1)");
    givenSampleCase("2\n", SECRET_EXPECTED);
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
}
