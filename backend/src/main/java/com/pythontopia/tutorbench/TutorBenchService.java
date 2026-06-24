package com.pythontopia.tutorbench;

import com.pythontopia.learner.effect.LearningEffectTraceService;
import org.springframework.stereotype.Service;

@Service
public class TutorBenchService {

  private final LearningEffectTraceService traceService;
  private final TutorBenchEvaluator evaluator;

  public TutorBenchService(LearningEffectTraceService traceService, TutorBenchEvaluator evaluator) {
    this.traceService = traceService;
    this.evaluator = evaluator;
  }

  public TutorBenchReportDto run(Long userId) {
    return evaluator.evaluate(traceService.summary(userId));
  }
}
