package com.pythontopia.learner.evaluation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pythontopia.learner.evaluation.service.LearnerEvaluationService;
import com.pythontopia.learner.evaluation.service.WeeklyEvaluationScheduler;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeeklyEvaluationSchedulerTest {

  private final LearnerEvaluationService service = mock(LearnerEvaluationService.class);

  @Test
  void generatesForEachEligibleLearnerAndIsolatesPerUserFailures() {
    when(service.findLearnersNeedingWeeklyEvaluation()).thenReturn(List.of(1L, 2L, 3L));
    // 2 号生成失败（如 graph 暂不可达）不得阻断 1、3 号。
    when(service.compute(eq(2L), isNull())).thenThrow(new RuntimeException("graph down"));

    WeeklyEvaluationScheduler scheduler = new WeeklyEvaluationScheduler(service, true);
    scheduler.generateWeeklyReports();

    verify(service).compute(eq(1L), isNull());
    verify(service).compute(eq(2L), isNull());
    verify(service).compute(eq(3L), isNull());
  }

  @Test
  void disabledFlagSkipsAllWork() {
    WeeklyEvaluationScheduler scheduler = new WeeklyEvaluationScheduler(service, false);

    scheduler.generateWeeklyReports();

    verify(service, never()).findLearnersNeedingWeeklyEvaluation();
    verify(service, never()).compute(any(), any());
  }
}
