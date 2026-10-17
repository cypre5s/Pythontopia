package com.pythontopia.submission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.learner.quest.service.QuestService;
import com.pythontopia.submission.dto.PublicProblemDto;
import com.pythontopia.submission.dto.SubmissionCreateRequest;
import com.pythontopia.submission.dto.SubmissionDto;
import com.pythontopia.submission.dto.SubmissionSource;
import com.pythontopia.submission.dto.SubmissionStatus;
import com.pythontopia.submission.dto.Verdict;
import com.pythontopia.submission.judge.JudgeService;
import com.pythontopia.submission.judge.JudgeService.JudgeOutcome;
import com.pythontopia.submission.repository.PracticeSubmissionRepository;
import com.pythontopia.submission.repository.PublicProblemRepository;
import com.pythontopia.submission.service.SubmissionService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

class SubmissionServiceTest {

  private final PracticeSubmissionRepository submissions = mock(PracticeSubmissionRepository.class);
  private final PublicProblemRepository problems = mock(PublicProblemRepository.class);
  private final JudgeService judge = mock(JudgeService.class);
  private final QuestService quests = mock(QuestService.class);
  private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
  private final SubmissionService service =
      new SubmissionService(submissions, problems, judge, quests, publisher);

  @Test
  void submitAcEmitsPracticeSucceededEvent() {
    when(submissions.countSubmittedAfter(anyLong(), any())).thenReturn(0);
    when(problems.findPublic(eq(43L), eq(101L)))
        .thenReturn(
            Optional.of(
                new PublicProblemDto(
                    101L,
                    43L,
                    "PY43-KC-001",
                    "题目",
                    "statement",
                    "input",
                    "output",
                    "basic",
                    null)));
    when(submissions.insertSubmission(anyLong(), anyLong(), any(), any(), any())).thenReturn(900L);
    when(judge.judge(eq(900L), eq(101L), any()))
        .thenReturn(new JudgeOutcome(Verdict.AC, 3, 3, null));
    when(problems.findKcCode(eq(101L))).thenReturn("PY43-KC-001");
    when(submissions.findById(eq(900L)))
        .thenReturn(Optional.of(sampleSubmission(900L, SubmissionStatus.PASSED)));

    SubmissionDto result =
        service.submit(
            7L,
            new SubmissionCreateRequest(
                101L, "print(1)", null, SubmissionSource.TRAINING, "wf-7-101-cafe"));
    assertThat(result.status()).isEqualTo(SubmissionStatus.PASSED);
    ArgumentCaptor<LearningEvent.PracticeSucceeded> eventCaptor =
        ArgumentCaptor.forClass(LearningEvent.PracticeSucceeded.class);
    verify(publisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().difficulty()).isEqualTo("basic");
    assertThat(eventCaptor.getValue().tutorWorkflowSessionKey()).isEqualTo("wf-7-101-cafe");
  }

  @Test
  void questAutoCompletionFailureDoesNotLogStudentIdentifiersOrExceptionMessage() {
    String sentinel = "private-quest-completion-error-sentinel";
    when(submissions.countSubmittedAfter(anyLong(), any())).thenReturn(0);
    when(problems.findPublic(eq(43L), eq(101L)))
        .thenReturn(
            Optional.of(
                new PublicProblemDto(
                    101L,
                    43L,
                    "PY43-KC-001",
                    "题目",
                    "statement",
                    "input",
                    "output",
                    "basic",
                    null)));
    when(submissions.insertSubmission(anyLong(), anyLong(), any(), any(), any())).thenReturn(903L);
    when(judge.judge(eq(903L), eq(101L), any()))
        .thenReturn(new JudgeOutcome(Verdict.AC, 3, 3, null));
    when(problems.findKcCode(eq(101L))).thenReturn("PY43-KC-001");
    when(submissions.findById(eq(903L)))
        .thenReturn(Optional.of(sampleSubmission(903L, SubmissionStatus.PASSED)));
    when(quests.complete(eq(7L), eq(55L), any()))
        .thenThrow(new BusinessException(HttpStatus.CONFLICT, "QUEST_STATE_CONFLICT", sentinel));

    Logger logger = (Logger) LoggerFactory.getLogger(SubmissionService.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      service.submit(
          7L, new SubmissionCreateRequest(101L, "print(1)", 55L, SubmissionSource.TRAINING));
    } finally {
      logger.detachAppender(appender);
    }

    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anyMatch(message -> message.contains("QUEST_STATE_CONFLICT"))
        .noneMatch(
            message ->
                message.contains(sentinel)
                    || message.contains("Quest 55")
                    || message.contains("submission 903"));
    assertThat(appender.list).noneMatch(event -> event.getThrowableProxy() != null);
  }

  @Test
  void submitWaEmitsPracticeFailedEvent() {
    when(submissions.countSubmittedAfter(anyLong(), any())).thenReturn(0);
    when(problems.findPublic(eq(43L), eq(101L)))
        .thenReturn(
            Optional.of(
                new PublicProblemDto(
                    101L,
                    43L,
                    "PY43-KC-001",
                    "题目",
                    "statement",
                    "input",
                    "output",
                    "basic",
                    null)));
    when(submissions.insertSubmission(anyLong(), anyLong(), any(), any(), any())).thenReturn(901L);
    when(judge.judge(eq(901L), eq(101L), any()))
        .thenReturn(new JudgeOutcome(Verdict.WA, 1, 3, "fail"));
    when(problems.findKcCode(eq(101L))).thenReturn("PY43-KC-001");
    when(submissions.findById(eq(901L)))
        .thenReturn(Optional.of(sampleSubmission(901L, SubmissionStatus.FAILED)));

    service.submit(
        7L, new SubmissionCreateRequest(101L, "print(1)", null, SubmissionSource.TRAINING));
    ArgumentCaptor<LearningEvent.PracticeFailed> eventCaptor =
        ArgumentCaptor.forClass(LearningEvent.PracticeFailed.class);
    verify(publisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().difficulty()).isEqualTo("basic");
  }

  /**
   * 回归：判题系统故障（SYSTEM_ERROR）不是学生答错，不得发 PracticeFailed 学习事件 （否则沙箱/解释器一挂，全体学生每次提交都扣掌握度、往画像写脏
   * error_pattern）。
   */
  @Test
  void submitSystemErrorDoesNotEmitPracticeFailedEvent() {
    String privateDetails = "private-judge-infrastructure-error-sentinel";
    when(submissions.countSubmittedAfter(anyLong(), any())).thenReturn(0);
    when(problems.findPublic(eq(43L), eq(101L)))
        .thenReturn(
            Optional.of(
                new PublicProblemDto(
                    101L,
                    43L,
                    "PY43-KC-001",
                    "题目",
                    "statement",
                    "input",
                    "output",
                    "basic",
                    null)));
    when(submissions.insertSubmission(anyLong(), anyLong(), any(), any(), any())).thenReturn(902L);
    when(judge.judge(eq(902L), eq(101L), any()))
        .thenReturn(new JudgeOutcome(Verdict.SYSTEM_ERROR, 0, 0, privateDetails));
    when(problems.findKcCode(eq(101L))).thenReturn("PY43-KC-001");
    when(submissions.findById(eq(902L)))
        .thenReturn(Optional.of(sampleSubmission(902L, SubmissionStatus.ERROR)));

    Logger logger = (Logger) LoggerFactory.getLogger(SubmissionService.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.setLevel(Level.ERROR);
    logger.addAppender(appender);
    SubmissionDto result;
    try {
      result =
          service.submit(
              7L, new SubmissionCreateRequest(101L, "print(1)", null, SubmissionSource.TRAINING));
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }

    assertThat(result.status()).isEqualTo(SubmissionStatus.ERROR);
    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .noneMatch(message -> message.contains("user 7") || message.contains(privateDetails));
    verifyNoInteractions(publisher);
  }

  @Test
  void submitRateLimited() {
    when(submissions.countSubmittedAfter(anyLong(), any())).thenReturn(1);
    when(problems.findPublic(eq(43L), eq(101L)))
        .thenReturn(
            Optional.of(
                new PublicProblemDto(101L, 43L, "PY43-KC-001", "题目", "", "", "", "basic", null)));
    assertThatThrownBy(
            () ->
                service.submit(
                    7L,
                    new SubmissionCreateRequest(101L, "print(1)", null, SubmissionSource.TRAINING)))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).code())
        .isEqualTo("SUBMISSION_THROTTLED");
  }

  @Test
  void submitFailsWhenProblemMissing() {
    when(submissions.countSubmittedAfter(anyLong(), any())).thenReturn(0);
    when(problems.findPublic(eq(43L), eq(101L))).thenReturn(Optional.empty());
    assertThatThrownBy(
            () ->
                service.submit(
                    7L,
                    new SubmissionCreateRequest(101L, "print(1)", null, SubmissionSource.TRAINING)))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).code())
        .isEqualTo("PROBLEM_NOT_FOUND");
  }

  private SubmissionDto sampleSubmission(Long id, SubmissionStatus status) {
    return new SubmissionDto(
        id,
        7L,
        101L,
        null,
        SubmissionSource.TRAINING,
        "PYTHON_3_11",
        "print(1)",
        OffsetDateTime.now(ZoneOffset.UTC),
        status,
        12,
        null);
  }
}
