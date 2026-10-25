package com.pythontopia.bugmine.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pythontopia.bugmine.dto.BugMineSessionDto;
import com.pythontopia.bugmine.dto.BugMineSessionStatus;
import com.pythontopia.bugmine.graph.DebugAgentClient;
import com.pythontopia.bugmine.graph.DebugAgentResponse;
import com.pythontopia.bugmine.judge.CounterExampleSelector;
import com.pythontopia.bugmine.judge.PythonTraceCollector;
import com.pythontopia.bugmine.repository.BugMineRepository;
import com.pythontopia.bugmine.repository.BugMineRepository.EchoCandidateRow;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.learner.mastery.review.ReviewStateService;
import com.pythontopia.submission.dto.SubmissionDto;
import com.pythontopia.submission.dto.SubmissionSource;
import com.pythontopia.submission.dto.SubmissionStatus;
import com.pythontopia.submission.repository.PracticeSubmissionRepository;
import com.pythontopia.submission.repository.PublicProblemRepository;
import com.pythontopia.submission.service.SubmissionService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class BugMineOpenThrottleTest {

  private static final long USER_ID = 7L;
  private static final String KC_CODE = "PY43-KC-001";

  @Mock private BugMineRepository repository;
  @Mock private PracticeSubmissionRepository submissionRepository;
  @Mock private PublicProblemRepository problemRepository;
  @Mock private PythonTraceCollector traceCollector;
  @Mock private CounterExampleSelector counterExampleSelector;
  @Mock private DebugAgentClient debugAgentClient;
  @Mock private SubmissionService submissionService;
  @Mock private ApplicationEventPublisher eventPublisher;
  private final ObjectMapper objectMapper = new ObjectMapper();
  @Mock private BugMineProfileAdvisor profileAdvisor;
  @Mock private ReviewStateService reviewStateService;
  @Mock private StudentWriteThrottle writeThrottle;
  private final PlatformTransactionManager transactionManager = new TestTransactionManager();
  private BugMineService service;

  @BeforeEach
  void setUp() {
    BugMineService target =
        new BugMineService(
            repository,
            submissionRepository,
            problemRepository,
            traceCollector,
            counterExampleSelector,
            debugAgentClient,
            submissionService,
            eventPublisher,
            objectMapper,
            profileAdvisor,
            reviewStateService,
            writeThrottle,
            transactionManager,
            3);
    ProxyFactory proxyFactory = new ProxyFactory(target);
    proxyFactory.addAdvice(
        new TransactionInterceptor(
            transactionManager, new AnnotationTransactionAttributeSource()));
    service = (BugMineService) proxyFactory.getProxy();
  }

  @Test
  void rejectsNewSessionBeforeLoadingSubmissionWhenAccountBudgetIsFull() {
    long submissionId = 99L;
    when(repository.findSessionIdBySubmission(USER_ID, submissionId))
        .thenReturn(java.util.Optional.empty());

    Throwable thrown = catchThrowable(() -> service.open(USER_ID, submissionId));

    verify(writeThrottle).tryAcquire(USER_ID, 10);
    verify(submissionRepository, never()).findSubmission(USER_ID, submissionId);
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> assertThat(error.code()).isEqualTo("BUG_MINE_OPEN_THROTTLED"));
  }

  @Test
  void rejectsEchoOpenBeforeCreatingSyntheticSubmissionWhenAccountBudgetIsFull() {
    when(repository.findEchoCandidates(USER_ID))
        .thenReturn(
            List.of(
                new EchoCandidateRow(
                    KC_CODE,
                    11L,
                    "计算思维",
                    BigDecimal.valueOf(0.6),
                    BigDecimal.valueOf(0.7),
                    null,
                    null,
                    null,
                    null)));

    Throwable thrown = catchThrowable(() -> service.openEcho(USER_ID, KC_CODE));
    verify(writeThrottle).tryAcquire(USER_ID, 10);
    verify(submissionRepository, never())
        .insertSubmission(any(), any(), any(), any(SubmissionSource.class), anyString());
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> assertThat(error.code()).isEqualTo("BUG_MINE_OPEN_THROTTLED"));
  }

  @Test
  void returnsExistingSessionWithoutConsumingAccountBudget() {
    long submissionId = 99L;
    BugMineSessionDto existing =
        new BugMineSessionDto(
            12L,
            USER_ID,
            submissionId,
            11L,
            null,
            BugMineSessionStatus.OPEN,
            0,
            0,
            1,
            false,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            List.of(),
            null);
    when(repository.findSessionIdBySubmission(USER_ID, submissionId))
        .thenReturn(java.util.Optional.of(existing.id()));
    when(repository.findSession(USER_ID, existing.id())).thenReturn(java.util.Optional.of(existing));
    when(repository.findSessionContext(USER_ID, existing.problemId(), submissionId))
        .thenReturn(java.util.Optional.empty());
    when(repository.findSubmissionSource(submissionId)).thenReturn(java.util.Optional.empty());
    when(problemRepository.findAccessible(43L, existing.problemId(), USER_ID))
        .thenReturn(java.util.Optional.empty());

    BugMineSessionDto result = service.open(USER_ID, submissionId);

    verify(writeThrottle, never()).tryAcquire(USER_ID, 10);
    assertThat(result.id()).isEqualTo(existing.id());
  }

  @Test
  void openRunsSandboxAndDebugGraphWithoutTransactionAndPersistsCardsInShortTransaction() {
    long submissionId = 99L;
    when(writeThrottle.tryAcquire(USER_ID, 10)).thenReturn(true);
    SubmissionDto submission = failedSubmission(submissionId);
    when(repository.findSessionIdBySubmission(USER_ID, submissionId))
        .thenReturn(Optional.empty());
    when(submissionRepository.findSubmission(USER_ID, submissionId))
        .thenReturn(Optional.of(submission));
    when(repository.findSessionContext(USER_ID, 11L, submissionId)).thenReturn(Optional.empty());
    when(problemRepository.findKcCode(11L)).thenReturn(KC_CODE);
    when(problemRepository.findVariantProblem(USER_ID, 11L, KC_CODE)).thenReturn(Optional.empty());
    when(counterExampleSelector.findFirstFailing(11L, USER_ID, submission.code()))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              return Optional.of(
                  new CounterExampleSelector.CounterExample("1\n", "1\n", "2\n", false));
            });
    when(traceCollector.collect(submission.code(), "1\n"))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              ObjectNode trace = new ObjectMapper().createObjectNode();
              trace.putArray("steps");
              return new PythonTraceCollector.TraceResult(trace, 0, false);
            });
    AtomicBoolean transactionDuringGraphCall = new AtomicBoolean(true);
    when(debugAgentClient.analyse(any()))
        .thenAnswer(
            invocation -> {
              transactionDuringGraphCall.set(
                  TransactionSynchronizationManager.isActualTransactionActive());
              return debugResponse();
            });
    when(repository.insertSession(USER_ID, submissionId, 11L, null, null, 0))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
              return 12L;
            });
    org.mockito.Mockito.doAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
              return 1;
            })
        .when(repository)
        .insertCard(eq(12L), any(), any(), eq(false));
    BugMineSessionDto stored = session(12L, submissionId);
    when(repository.findSession(USER_ID, 12L)).thenReturn(Optional.of(stored));
    when(repository.findSubmissionSource(submissionId)).thenReturn(Optional.empty());
    when(problemRepository.findAccessible(43L, 11L, USER_ID)).thenReturn(Optional.empty());

    BugMineSessionDto result = service.open(USER_ID, submissionId);

    assertThat(transactionDuringGraphCall).isFalse();
    assertThat(result.id()).isEqualTo(12L);
    verify(repository, times(3)).insertCard(eq(12L), any(), any(), eq(false));
  }

  @Test
  void openEchoDoesNotKeepSyntheticSubmissionTransactionOpenDuringDebugGraph() {
    when(writeThrottle.tryAcquire(USER_ID, 10)).thenReturn(true);
    when(repository.findEchoCandidates(USER_ID))
        .thenReturn(
            List.of(
                new EchoCandidateRow(
                    KC_CODE,
                    11L,
                    "计算思维",
                    BigDecimal.valueOf(0.6),
                    BigDecimal.valueOf(0.7),
                    null,
                    null,
                    null,
                    null)));
    when(submissionRepository.insertSubmission(
            eq(USER_ID), eq(11L), eq(null), eq(SubmissionSource.BUG_MINE_ECHO), anyString()))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
              return 99L;
            });
    when(submissionRepository.findSubmission(USER_ID, 99L))
        .thenReturn(Optional.of(failedSubmission(99L)));
    when(repository.findSessionIdBySubmission(USER_ID, 99L)).thenReturn(Optional.empty());
    when(repository.findSessionContext(USER_ID, 11L, 99L)).thenReturn(Optional.empty());
    when(problemRepository.findKcCode(11L)).thenReturn(KC_CODE);
    when(problemRepository.findVariantProblem(USER_ID, 11L, KC_CODE)).thenReturn(Optional.empty());
    when(counterExampleSelector.findFirstFailing(11L, USER_ID, "print('x')"))
        .thenReturn(
            Optional.of(new CounterExampleSelector.CounterExample("", "", "", true)));
    AtomicBoolean transactionDuringGraphCall = new AtomicBoolean(true);
    when(debugAgentClient.analyse(any()))
        .thenAnswer(
            invocation -> {
              transactionDuringGraphCall.set(
                  TransactionSynchronizationManager.isActualTransactionActive());
              return debugResponse();
            });
    when(repository.insertSession(USER_ID, 99L, 11L, null, null, 0)).thenReturn(12L);
    BugMineSessionDto stored = session(12L, 99L);
    when(repository.findSession(USER_ID, 12L)).thenReturn(Optional.of(stored));
    when(repository.findSubmissionSource(99L)).thenReturn(Optional.of(SubmissionSource.BUG_MINE_ECHO));
    when(problemRepository.findAccessible(43L, 11L, USER_ID)).thenReturn(Optional.empty());
    service.openEcho(USER_ID, KC_CODE);

    assertThat(transactionDuringGraphCall).isFalse();
  }

  @Test
  void openEchoRemovesSyntheticSubmissionWhenDebugGraphFails() {
    when(writeThrottle.tryAcquire(USER_ID, 10)).thenReturn(true);
    when(repository.findEchoCandidates(USER_ID))
        .thenReturn(
            List.of(
                new EchoCandidateRow(
                    KC_CODE,
                    11L,
                    "计算思维",
                    BigDecimal.valueOf(0.6),
                    BigDecimal.valueOf(0.7),
                    null,
                    null,
                    null,
                    null)));
    when(submissionRepository.insertSubmission(
            eq(USER_ID), eq(11L), eq(null), eq(SubmissionSource.BUG_MINE_ECHO), anyString()))
        .thenReturn(99L);
    when(submissionRepository.findSubmission(USER_ID, 99L))
        .thenReturn(Optional.of(failedSubmission(99L)));
    when(repository.findSessionIdBySubmission(USER_ID, 99L)).thenReturn(Optional.empty());
    when(repository.findSessionContext(USER_ID, 11L, 99L)).thenReturn(Optional.empty());
    when(problemRepository.findKcCode(11L)).thenReturn(KC_CODE);
    when(problemRepository.findVariantProblem(USER_ID, 11L, KC_CODE)).thenReturn(Optional.empty());
    when(counterExampleSelector.findFirstFailing(11L, USER_ID, "print('x')"))
        .thenReturn(
            Optional.of(new CounterExampleSelector.CounterExample("", "", "", true)));
    when(debugAgentClient.analyse(any())).thenReturn(null);
    doAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
              return 1;
            })
        .when(submissionRepository)
        .deleteSyntheticEchoSubmission(USER_ID, 99L);

    Throwable thrown = catchThrowable(() -> service.openEcho(USER_ID, KC_CODE));

    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> assertThat(error.code()).isEqualTo("DEBUG_GRAPH_EMPTY"));
    verify(submissionRepository).deleteSyntheticEchoSubmission(USER_ID, 99L);
  }

  private static SubmissionDto failedSubmission(long submissionId) {
    return new SubmissionDto(
        submissionId,
        USER_ID,
        11L,
        null,
        SubmissionSource.TRAINING,
        "PYTHON_3_11",
        "print('x')",
        OffsetDateTime.now(),
        SubmissionStatus.FAILED,
        null,
        null);
  }

  private static DebugAgentResponse debugResponse() {
    return new DebugAgentResponse(
        new DebugAgentResponse.CounterExamplePayload("", "", "", "", false),
        new DebugAgentResponse.VariableTracePayload(List.of(), false),
        new DebugAgentResponse.WrongAssumption("", "", List.of()),
        List.of(),
        null,
        true,
        List.of());
  }

  private static BugMineSessionDto session(long sessionId, long submissionId) {
    return new BugMineSessionDto(
        sessionId,
        USER_ID,
        submissionId,
        11L,
        null,
        BugMineSessionStatus.OPEN,
        0,
        0,
        1,
        false,
        null,
        null,
        null,
        null,
        null,
        List.of(),
        List.of(),
        null);
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
