package com.pythontopia.submission;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.springframework.context.ApplicationEventPublisher;

/** 情绪化小镇②：练习提交派生 重试 / 逆袭 行为信号。 */
class SubmissionEmotionSignalTest {

  private final PracticeSubmissionRepository submissions = mock(PracticeSubmissionRepository.class);
  private final PublicProblemRepository problems = mock(PublicProblemRepository.class);
  private final JudgeService judge = mock(JudgeService.class);
  private final QuestService quests = mock(QuestService.class);
  private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
  private final SubmissionService service =
      new SubmissionService(submissions, problems, judge, quests, publisher);

  @Test
  void acAfterTwoPriorFails_emitsComeback() {
    stubProblem();
    when(submissions.insertSubmission(anyLong(), anyLong(), any(), any(), any())).thenReturn(902L);
    when(judge.judge(eq(902L), eq(101L), any(), eq(7L))).thenReturn(new JudgeOutcome(Verdict.AC, 3, 3, null));
    when(problems.findKcCode(eq(101L))).thenReturn("PY43-KC-001");
    when(submissions.countPriorFailures(eq(7L), eq(101L), eq(902L))).thenReturn(2);
    when(submissions.findById(eq(902L)))
        .thenReturn(Optional.of(sample(902L, SubmissionStatus.PASSED)));

    service.submit(7L, new SubmissionCreateRequest(101L, "print(1)", null, SubmissionSource.TRAINING));
    verify(publisher).publishEvent(any(LearningEvent.PracticeSucceeded.class));
    verify(publisher).publishEvent(any(LearningEvent.ComebackSolved.class));
  }

  @Test
  void failAfterOnePriorFail_emitsRetried() {
    stubProblem();
    when(submissions.insertSubmission(anyLong(), anyLong(), any(), any(), any())).thenReturn(903L);
    when(judge.judge(eq(903L), eq(101L), any(), eq(7L))).thenReturn(new JudgeOutcome(Verdict.WA, 1, 3, "fail"));
    when(problems.findKcCode(eq(101L))).thenReturn("PY43-KC-001");
    when(submissions.countPriorFailures(eq(7L), eq(101L), eq(903L))).thenReturn(1);
    when(submissions.findById(eq(903L)))
        .thenReturn(Optional.of(sample(903L, SubmissionStatus.FAILED)));

    service.submit(7L, new SubmissionCreateRequest(101L, "print(1)", null, SubmissionSource.TRAINING));
    verify(publisher).publishEvent(any(LearningEvent.PracticeFailed.class));
    verify(publisher).publishEvent(any(LearningEvent.PracticeRetried.class));
  }

  @Test
  void acFirstTry_noComeback() {
    stubProblem();
    when(submissions.insertSubmission(anyLong(), anyLong(), any(), any(), any())).thenReturn(904L);
    when(judge.judge(eq(904L), eq(101L), any(), eq(7L))).thenReturn(new JudgeOutcome(Verdict.AC, 3, 3, null));
    when(problems.findKcCode(eq(101L))).thenReturn("PY43-KC-001");
    when(submissions.countPriorFailures(eq(7L), eq(101L), eq(904L))).thenReturn(0);
    when(submissions.findById(eq(904L)))
        .thenReturn(Optional.of(sample(904L, SubmissionStatus.PASSED)));

    service.submit(7L, new SubmissionCreateRequest(101L, "print(1)", null, SubmissionSource.TRAINING));
    verify(publisher).publishEvent(any(LearningEvent.PracticeSucceeded.class));
    verify(publisher, never()).publishEvent(any(LearningEvent.ComebackSolved.class));
  }

  private void stubProblem() {
    when(submissions.countSubmittedAfter(anyLong(), any())).thenReturn(0);
    when(problems.findAccessible(eq(43L), eq(101L), eq(7L)))
        .thenReturn(
            Optional.of(
                new PublicProblemDto(
                    101L, 43L, "PY43-KC-001", "题目", "stmt", "in", "out", "basic", null)));
  }

  private SubmissionDto sample(Long id, SubmissionStatus status) {
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
