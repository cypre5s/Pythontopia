package com.pythontopia.tutorbench;

import static org.assertj.core.api.Assertions.assertThat;

import com.pythontopia.learner.effect.LearningEffectSummaryDto;
import com.pythontopia.learner.evidence.EvidenceGraphMetricsDto;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** TutorBench-lite：用真实学习闭环 trace 的数字证明教学任务、反作弊、证据一致性。 */
class TutorBenchEvaluatorTest {

  private final TutorBenchEvaluator evaluator = new TutorBenchEvaluator();

  @Test
  void passesAllCasesWhenLearningLoopIsEvidenceClosedAndTraceConsistent() {
    LearningEffectSummaryDto summary =
        new LearningEffectSummaryDto(
            1,
            1,
            1,
            2,
            1,
            1,
            0.5,
            1.0,
            1.0,
            new BigDecimal("0.030"),
            0,
            2,
            graphMetrics(1.0, 1.0, 1.0, 1.0),
            1);

    TutorBenchReportDto report = evaluator.evaluate(summary);

    assertThat(report.totalCases()).isEqualTo(6);
    assertThat(report.passedCases()).isEqualTo(6);
    assertThat(report.passRate()).isEqualTo(1.0);
    assertThat(report.cases()).extracting(TutorBenchCaseDto::name)
        .containsExactly(
            "multi_turn_learning_closure",
            "anti_cheat_remediation",
            "evidence_consistency",
            "evidence_graph_connectivity",
            "grounded_personalization",
            "trajectory_traceability");
    assertThat(report.cases()).allSatisfy(item -> assertThat(item.passed()).isTrue());
  }

  @Test
  void noFailedChecksPassesRemediationCaseWhenMasteryDeltaIsEvidenceBounded() {
    LearningEffectSummaryDto summary =
        new LearningEffectSummaryDto(
            1,
            1,
            1,
            1,
            1,
            0,
            1.0,
            1.0,
            0.0,
            new BigDecimal("0.050"),
            0,
            1,
            graphMetrics(1.0, 1.0, 1.0, 1.0),
            1);

    TutorBenchCaseDto antiCheat = antiCheatCase(evaluator.evaluate(summary));

    assertThat(antiCheat.passed()).isTrue();
    assertThat(antiCheat.score()).isEqualTo(1.0);
    assertThat(antiCheat.reason()).contains("无需补救");
  }

  @Test
  void noFailedChecksStillFailsRemediationCaseWhenMasteryDeltaLooksInflated() {
    LearningEffectSummaryDto summary =
        new LearningEffectSummaryDto(
            1,
            1,
            1,
            1,
            1,
            0,
            1.0,
            1.0,
            0.0,
            new BigDecimal("0.200"),
            0,
            1,
            graphMetrics(1.0, 1.0, 1.0, 1.0),
            1);

    TutorBenchCaseDto antiCheat = antiCheatCase(evaluator.evaluate(summary));

    assertThat(antiCheat.passed()).isFalse();
    assertThat(antiCheat.reason()).contains("刷分风险");
  }

  @Test
  void failsEvidenceConsistencyWhenConsumptionStillRewardsResourceReadDirectly() {
    LearningEffectSummaryDto summary =
        new LearningEffectSummaryDto(
            1,
            1,
            0,
            0,
            0,
            0,
            0.0,
            0.0,
            0.0,
            BigDecimal.ZERO,
            1,
            0,
            graphMetrics(0.0, 0.0, 0.0, 0.0),
            0);

    TutorBenchReportDto report = evaluator.evaluate(summary);

    assertThat(report.totalCases()).isEqualTo(6);
    assertThat(report.passedCases()).isLessThan(6);
    TutorBenchCaseDto evidence =
        report.cases().stream()
            .filter(item -> "evidence_consistency".equals(item.name()))
            .findFirst()
            .orElseThrow();
    assertThat(evidence.passed()).isFalse();
    assertThat(evidence.score()).isEqualTo(0.0);
    assertThat(evidence.reason()).contains("RESOURCE_READ");
  }

  @Test
  void graphBackedSummaryScoresHigherThanNaiveSummary() {
    LearningEffectSummaryDto naive =
        new LearningEffectSummaryDto(
            1,
            1,
            0,
            0,
            0,
            0,
            0.0,
            0.0,
            0.0,
            BigDecimal.ZERO,
            0,
            0,
            graphMetrics(0.0, 0.0, 0.0, 0.2),
            0);
    LearningEffectSummaryDto graphBacked =
        new LearningEffectSummaryDto(
            1,
            1,
            1,
            2,
            1,
            1,
            0.5,
            1.0,
            1.0,
            new BigDecimal("0.030"),
            0,
            2,
            graphMetrics(1.0, 1.0, 1.0, 1.0),
            1);

    TutorBenchReportDto naiveReport = evaluator.evaluate(naive);
    TutorBenchReportDto graphReport = evaluator.evaluate(graphBacked);

    assertThat(graphReport.passRate()).isGreaterThan(naiveReport.passRate());
    assertThat(graphReport.passRate() - naiveReport.passRate()).isGreaterThanOrEqualTo(0.5);
  }

  private static TutorBenchCaseDto antiCheatCase(TutorBenchReportDto report) {
    return report.cases().stream()
        .filter(item -> "anti_cheat_remediation".equals(item.name()))
        .findFirst()
        .orElseThrow();
  }

  private static EvidenceGraphMetricsDto graphMetrics(
      double checkCoverage, double masteryEvidence, double connectedRate, double completeness) {
    return new EvidenceGraphMetricsDto(
        12,
        18,
        2,
        completeness > 0.5 ? 2 : 0,
        checkCoverage,
        masteryEvidence,
        connectedRate,
        0.5,
        completeness,
        connectedRate > 0.8 ? 0 : 3);
  }
}
