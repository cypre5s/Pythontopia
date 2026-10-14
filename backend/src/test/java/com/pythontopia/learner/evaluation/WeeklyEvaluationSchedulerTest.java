package com.pythontopia.learner.evaluation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pythontopia.common.error.BusinessException;
import com.pythontopia.tutor.TutorDailyRequestLimitService;
import com.pythontopia.learner.evaluation.service.LearnerEvaluationService;
import com.pythontopia.learner.evaluation.service.WeeklyEvaluationScheduler;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.Test;

class WeeklyEvaluationSchedulerTest {

  private final LearnerEvaluationService service = mock(LearnerEvaluationService.class);
  private final TutorDailyRequestLimitService requestLimitService =
      mock(TutorDailyRequestLimitService.class);

  @Test
  void generatesForEachEligibleLearnerAndIsolatesPerUserFailures() {
    when(service.findLearnersNeedingWeeklyEvaluation()).thenReturn(List.of(1L, 2L, 3L));
    // 2 号生成失败（如 graph 暂不可达）不得阻断 1、3 号。
    when(service.compute(eq(2L), isNull())).thenThrow(new RuntimeException("graph down"));

    WeeklyEvaluationScheduler scheduler =
        new WeeklyEvaluationScheduler(service, requestLimitService, true);
    scheduler.generateWeeklyReports();

    verify(requestLimitService).reserve(1L);
    verify(requestLimitService).reserve(2L);
    verify(requestLimitService).reserve(3L);
    verify(service).compute(eq(1L), isNull());
    verify(service).compute(eq(2L), isNull());
    verify(service).compute(eq(3L), isNull());
  }

  @Test
  void stopsTheBatchWhenTheSharedProviderBudgetIsExhausted() {
    when(service.findLearnersNeedingWeeklyEvaluation()).thenReturn(List.of(1L, 2L, 3L));
    org.mockito.Mockito.doThrow(
            new BusinessException(
                HttpStatus.TOO_MANY_REQUESTS,
                "AI_PROVIDER_DAILY_LIMIT_REACHED",
                "provider limit"))
        .when(requestLimitService)
        .reserve(2L);

    WeeklyEvaluationScheduler scheduler =
        new WeeklyEvaluationScheduler(service, requestLimitService, true);
    scheduler.generateWeeklyReports();

    verify(service).compute(eq(1L), isNull());
    verify(service, never()).compute(eq(2L), any());
    verify(service, never()).compute(eq(3L), any());
  }

  @Test
  void disabledFlagSkipsAllWork() {
    WeeklyEvaluationScheduler scheduler =
        new WeeklyEvaluationScheduler(service, requestLimitService, false);

    scheduler.generateWeeklyReports();

    verify(service, never()).findLearnersNeedingWeeklyEvaluation();
    verify(service, never()).compute(any(), any());
  }
}
