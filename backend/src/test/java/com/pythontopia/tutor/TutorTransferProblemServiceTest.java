package com.pythontopia.tutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.submission.dto.PublicProblemDto;
import com.pythontopia.submission.dto.Verdict;
import com.pythontopia.submission.judge.JudgeCaseEvaluator;
import com.pythontopia.submission.judge.JudgeWorkerPool;
import com.pythontopia.submission.judge.StaticCodeChecker;
import com.pythontopia.submission.repository.PublicProblemRepository;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class TutorTransferProblemServiceTest {

  private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
  private final PublicProblemRepository problemRepository = mock(PublicProblemRepository.class);
  private final StaticCodeChecker staticCodeChecker = mock(StaticCodeChecker.class);
  private final JudgeWorkerPool judgeWorkerPool = mock(JudgeWorkerPool.class);
  private final JudgeCaseEvaluator caseEvaluator = mock(JudgeCaseEvaluator.class);
  private final PlatformTransactionManager transactionManager = new TestTransactionManager();
  private final TutorTransferProblemService service =
      new TutorTransferProblemService(
          jdbcTemplate,
          new ObjectMapper(),
          problemRepository,
          staticCodeChecker,
          judgeWorkerPool,
          caseEvaluator,
          transactionManager);

  @Test
  void referenceSandboxRunsOutsideTransactionAndInsertUsesShortTransaction() throws Exception {
    AtomicBoolean transactionDuringStaticCheck = new AtomicBoolean(true);
    AtomicBoolean transactionDuringEvaluation = new AtomicBoolean(true);
    AtomicBoolean transactionDuringInsert = new AtomicBoolean(false);
    when(problemRepository.findPublic(43L, 476L))
        .thenReturn(Optional.of(sourceProblem("basic")));
    when(staticCodeChecker.check(anyString()))
        .thenAnswer(
            invocation -> {
              transactionDuringStaticCheck.set(
                  TransactionSynchronizationManager.isActualTransactionActive());
              return new StaticCodeChecker.CheckResult(true, "", false);
            });
    when(judgeWorkerPool.submitAndWait(any()))
        .thenAnswer(invocation -> invocation.<java.util.concurrent.Callable<?>>getArgument(0).call());
    when(caseEvaluator.evaluate(anyString(), any()))
        .thenAnswer(
            invocation -> {
              transactionDuringEvaluation.set(
                  TransactionSynchronizationManager.isActualTransactionActive());
              var cases = (java.util.List<?>) invocation.getArgument(1);
              return new JudgeCaseEvaluator.CaseEvaluation(
                  Verdict.AC, cases.size(), 3, 64, null, new ObjectMapper().createObjectNode());
            });
    when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(1_000_000_001L);
    when(jdbcTemplate.update(anyString(), any(Object[].class)))
        .thenAnswer(
            invocation -> {
              transactionDuringInsert.set(
                  TransactionSynchronizationManager.isActualTransactionActive());
              return 1;
            });

    ProxyFactory proxyFactory = new ProxyFactory(service);
    proxyFactory.addAdvice(
        new TransactionInterceptor(
            transactionManager, new AnnotationTransactionAttributeSource()));
    TutorTransferProblemService transactionalService =
        (TutorTransferProblemService) proxyFactory.getProxy();
    transactionalService.materialize(7L, 11L, UUID.randomUUID(), 476L, transferDraft());

    assertThat(transactionDuringStaticCheck).isFalse();
    assertThat(transactionDuringEvaluation).isFalse();
    assertThat(transactionDuringInsert).isTrue();
  }

  @Test
  void refusesTransferWhenReferenceSolutionDoesNotPassGeneratedCases() throws Exception {
    when(problemRepository.findPublic(43L, 476L))
        .thenReturn(Optional.of(sourceProblem("basic")));
    when(staticCodeChecker.check(anyString()))
        .thenReturn(new StaticCodeChecker.CheckResult(true, "", false));
    when(judgeWorkerPool.submitAndWait(any()))
        .thenAnswer(invocation -> invocation.<java.util.concurrent.Callable<?>>getArgument(0).call());
    when(caseEvaluator.evaluate(anyString(), any()))
        .thenReturn(
            new JudgeCaseEvaluator.CaseEvaluation(
                Verdict.WA, 1, 3, 64, "private generated case mismatch", new ObjectMapper().createObjectNode()));

    assertThatThrownBy(
            () -> service.materialize(7L, 11L, UUID.randomUUID(), 476L, transferDraft()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("自测未通过");

    verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
  }

  @ParameterizedTest
  @CsvSource({"basic, medium", "medium, hard", "hard, hard"})
  void raisesTransferDifficultyWithinTheSupportedRange(String sourceDifficulty, String expectedDifficulty) throws Exception {
    when(problemRepository.findPublic(43L, 476L))
        .thenReturn(Optional.of(sourceProblem(sourceDifficulty)));
    when(staticCodeChecker.check(anyString()))
        .thenReturn(new StaticCodeChecker.CheckResult(true, "", false));
    when(judgeWorkerPool.submitAndWait(any()))
        .thenAnswer(invocation -> invocation.<java.util.concurrent.Callable<?>>getArgument(0).call());
    when(caseEvaluator.evaluate(anyString(), any()))
        .thenAnswer(
            invocation -> {
              var cases = (java.util.List<?>) invocation.getArgument(1);
              return new JudgeCaseEvaluator.CaseEvaluation(
                  Verdict.AC, cases.size(), 3, 64, null, new ObjectMapper().createObjectNode());
            });
    when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(1_000_000_001L);
    when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

    PublicProblemDto result = service.materialize(7L, 11L, UUID.randomUUID(), 476L, transferDraft());

    assertThat(result.id()).isEqualTo(1_000_000_001L);
    assertThat(result.kcCode()).isEqualTo("PY43-KC-016");
    assertThat(result.title()).isEqualTo("矩形面积");
    assertThat(result.difficulty()).isEqualTo(expectedDifficulty);
    assertThat(result.samples().get(0).path("output").asText()).isEqualTo("6.00");
    ArgumentCaptor<Object[]> insertArguments = ArgumentCaptor.forClass(Object[].class);
    verify(jdbcTemplate).update(anyString(), insertArguments.capture());
    assertThat(insertArguments.getValue()[6]).isEqualTo(expectedDifficulty);
  }

  private PublicProblemDto sourceProblem(String difficulty) {
    return new PublicProblemDto(
        476L, 43L, "PY43-KC-016", "圆面积", "计算圆面积", "半径", "面积", difficulty, null);
  }

  private com.fasterxml.jackson.databind.JsonNode transferDraft() throws Exception {
    return new ObjectMapper()
        .readTree(
            """
            {
              "title":"矩形面积",
              "description":"读取长和宽，输出矩形面积。",
              "input_description":"两个浮点数",
              "output_description":"面积，保留两位小数",
              "samples":[{"input":"2 3","output":"6.00"}],
              "hint":"先读清两个输入值。",
              "target_kcs":["PY43-KC-016"],
              "reference_solution_language":"Python3",
              "reference_solution_code":"length, width = map(float, input().split())\\nprint(f'{length * width:.2f}')",
              "test_cases":[{"input":"1.5 4","output":"6.00"}]
            }
            """);
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
