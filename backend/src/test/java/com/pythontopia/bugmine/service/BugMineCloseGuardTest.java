package com.pythontopia.bugmine.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
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
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.learner.mastery.review.ReviewStateService;
import com.pythontopia.submission.repository.PracticeSubmissionRepository;
import com.pythontopia.submission.repository.PublicProblemRepository;
import com.pythontopia.submission.service.SubmissionService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/**
 * BugMine {@code close} 终态守卫回归：重复 POST 不得重复发 {@code PracticeAbandoned}
 * （否则会反复改画像、写记忆流），也不得把已 RESOLVED 的会话改判成放弃。
 */
class BugMineCloseGuardTest {

  private final BugMineRepository repository = mock(BugMineRepository.class);
  private final PracticeSubmissionRepository submissionRepository =
      mock(PracticeSubmissionRepository.class);
  private final PublicProblemRepository problemRepository = mock(PublicProblemRepository.class);
  private final PythonTraceCollector traceCollector = mock(PythonTraceCollector.class);
  private final CounterExampleSelector counterExampleSelector =
      mock(CounterExampleSelector.class);
  private final DebugAgentClient debugAgentClient = mock(DebugAgentClient.class);
  private final SubmissionService submissionService = mock(SubmissionService.class);
  private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
  private final ReviewStateService reviewStateService = mock(ReviewStateService.class);
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
            new ObjectMapper(),
            new BugMineProfileAdvisor(),
            reviewStateService,
            mock(StudentWriteThrottle.class),
            3);
    // enrichSession 的旁路依赖：无上下文 / 无来源 / 无题干均按缺省处理。
    lenient()
        .when(repository.findSessionContext(anyLong(), anyLong(), anyLong()))
        .thenReturn(Optional.empty());
    lenient().when(repository.findSubmissionSource(anyLong())).thenReturn(Optional.empty());
    lenient().when(problemRepository.findAccessible(anyLong(), anyLong(), anyLong()))
        .thenReturn(Optional.empty());
    lenient().when(problemRepository.findKcCode(anyLong())).thenReturn("PY43-KC-001");
  }

  @Test
  void closingAlreadyAbandonedSessionConflictsAndPublishesNoEvent() {
    when(repository.findSession(eq(7L), eq(55L)))
        .thenReturn(Optional.of(session(BugMineSessionStatus.ABANDONED)));

    assertThatThrownBy(() -> service.close(7L, 55L, BugMineSessionStatus.ABANDONED))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).code())
        .isEqualTo("BUG_MINE_SESSION_CLOSED");

    verify(repository, never()).updateStatus(anyLong(), any());
    verify(eventPublisher, never()).publishEvent(any(LearningEvent.PracticeAbandoned.class));
  }

  @Test
  void resolvedSessionCannotBeReclassifiedAsAbandoned() {
    when(repository.findSession(eq(7L), eq(55L)))
        .thenReturn(Optional.of(session(BugMineSessionStatus.RESOLVED)));

    assertThatThrownBy(() -> service.close(7L, 55L, BugMineSessionStatus.ABANDONED))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).code())
        .isEqualTo("BUG_MINE_SESSION_CLOSED");

    verify(repository, never()).updateStatus(anyLong(), any());
  }

  /** 并发下 SQL 谓词才是仲裁者：影响 0 行说明别的请求已先关闭，不得再发事件。 */
  @Test
  void concurrentCloseLosingRaceConflictsWithoutEvent() {
    when(repository.findSession(eq(7L), eq(55L)))
        .thenReturn(Optional.of(session(BugMineSessionStatus.OPEN)));
    when(repository.updateStatus(eq(55L), eq(BugMineSessionStatus.ABANDONED))).thenReturn(0);

    assertThatThrownBy(() -> service.close(7L, 55L, BugMineSessionStatus.ABANDONED))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).code())
        .isEqualTo("BUG_MINE_SESSION_CLOSED");

    verify(eventPublisher, never()).publishEvent(any(LearningEvent.PracticeAbandoned.class));
  }

  @Test
  void firstCloseOfOpenSessionStillPublishesAbandonedEvent() {
    when(repository.findSession(eq(7L), eq(55L)))
        .thenReturn(Optional.of(session(BugMineSessionStatus.OPEN)))
        .thenReturn(Optional.of(session(BugMineSessionStatus.ABANDONED)));
    when(repository.updateStatus(eq(55L), eq(BugMineSessionStatus.ABANDONED))).thenReturn(1);

    BugMineSessionDto closed = service.close(7L, 55L, BugMineSessionStatus.ABANDONED);

    assertThat(closed.status()).isEqualTo(BugMineSessionStatus.ABANDONED);
    verify(eventPublisher).publishEvent(any(LearningEvent.PracticeAbandoned.class));
  }

  private BugMineSessionDto session(BugMineSessionStatus status) {
    return new BugMineSessionDto(
        55L,
        7L,
        900L,
        101L,
        null,
        status,
        0,
        1,
        3,
        false,
        null,
        OffsetDateTime.now(ZoneOffset.UTC),
        null,
        null,
        null,
        List.of(),
        List.of(),
        null);
  }
}
