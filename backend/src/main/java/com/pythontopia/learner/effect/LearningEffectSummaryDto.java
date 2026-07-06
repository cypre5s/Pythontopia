package com.pythontopia.learner.effect;

import com.pythontopia.learner.evidence.EvidenceGraphMetricsDto;
import java.math.BigDecimal;

public record LearningEffectSummaryDto(
    int resourceCount,
    int consumedResourceCount,
    int checkedResourceCount,
    int checkCount,
    int passedCheckCount,
    int failedCheckCount,
    double checkPassRate,
    double evidenceCompletionRate,
    double remediationRate,
    BigDecimal totalMasteryDelta,
    int positiveResourceReadEvents,
    int checkMasteryEvents,
    EvidenceGraphMetricsDto evidenceGraphMetrics,
    int personalizationSignalCount) {

  private static final EvidenceGraphMetricsDto EMPTY_GRAPH_METRICS =
      new EvidenceGraphMetricsDto(0, 0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0);

  public LearningEffectSummaryDto {
    if (totalMasteryDelta == null) {
      totalMasteryDelta = BigDecimal.ZERO;
    }
    if (evidenceGraphMetrics == null) {
      evidenceGraphMetrics = EMPTY_GRAPH_METRICS;
    }
  }

  public LearningEffectSummaryDto(
      int resourceCount,
      int consumedResourceCount,
      int checkedResourceCount,
      int checkCount,
      int passedCheckCount,
      int failedCheckCount,
      double checkPassRate,
      double evidenceCompletionRate,
      double remediationRate,
      BigDecimal totalMasteryDelta,
      int positiveResourceReadEvents,
      int checkMasteryEvents) {
    this(
        resourceCount,
        consumedResourceCount,
        checkedResourceCount,
        checkCount,
        passedCheckCount,
        failedCheckCount,
        checkPassRate,
        evidenceCompletionRate,
        remediationRate,
        totalMasteryDelta,
        positiveResourceReadEvents,
        checkMasteryEvents,
        EMPTY_GRAPH_METRICS,
        0);
  }

  public LearningEffectSummaryDto withEvidenceGraph(
      EvidenceGraphMetricsDto metrics, int signalCount) {
    return new LearningEffectSummaryDto(
        resourceCount,
        consumedResourceCount,
        checkedResourceCount,
        checkCount,
        passedCheckCount,
        failedCheckCount,
        checkPassRate,
        evidenceCompletionRate,
        remediationRate,
        totalMasteryDelta,
        positiveResourceReadEvents,
        checkMasteryEvents,
        metrics,
        signalCount);
  }
}
