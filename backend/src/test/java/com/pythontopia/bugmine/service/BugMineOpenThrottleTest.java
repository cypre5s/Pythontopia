package com.pythontopia.bugmine.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.bugmine.dto.BugMineSessionDto;
import com.pythontopia.bugmine.dto.BugMineSessionStatus;
import com.pythontopia.bugmine.graph.DebugAgentClient;
import com.pythontopia.bugmine.judge.CounterExampleSelector;
import com.pythontopia.bugmine.judge.PythonTraceCollector;
import com.pythontopia.bugmine.repository.BugMineRepository;
import com.pythontopia.bugmine.repository.BugMineRepository.EchoCandidateRow;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.learner.mastery.review.ReviewStateService;
import com.pythontopia.submission.dto.SubmissionSource;
import com.pythontopia.submission.repository.PracticeSubmissionRepository;
import com.pythontopia.submission.repository.PublicProblemRepository;
import com.pythontopia.submission.service.SubmissionService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

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
  private BugMineService service;

  @BeforeEach
  void setUp() {
    service =
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
            3);
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
    when(problemRepository.findPublic(43L, existing.problemId())).thenReturn(java.util.Optional.empty());

    BugMineSessionDto result = service.open(USER_ID, submissionId);

    verify(writeThrottle, never()).tryAcquire(USER_ID, 10);
    assertThat(result.id()).isEqualTo(existing.id());
  }
}
