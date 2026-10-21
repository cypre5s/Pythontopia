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
            0.5,
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
            0.5,
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

  /** 零失败微测的优等生不是刷分嫌疑：补救率不适用（N/A），只看有没有超额加分。 */
  @Test
  void studentWhoNeverFailedAnyCheckIsNotFlaggedAsGaming() {
    LearningEffectSummaryDto summary =
        new LearningEffectSummaryDto(
            2,
            2,
            2,
            4,
            4,
            0,
            1.0,
            1.0,
            0.0,
            new BigDecimal("0.120"),
            0,
            4,
            graphMetrics(1.0, 1.0, 1.0, 1.0),
            2);

    TutorBenchReportDto report = evaluator.evaluate(summary);

    TutorBenchCaseDto antiCheat = caseNamed(report, "anti_cheat_remediation");
    assertThat(antiCheat.passed()).isTrue();
    assertThat(antiCheat.score()).isEqualTo(1.0);
    assertThat(antiCheat.reason()).contains("不适用");
    assertThat(antiCheat.reason()).doesNotContain("未触发补救");
  }

  /** 全新零活跃用户：什么都没做，同样不该被报成刷分风险。 */
  @Test
  void brandNewUserWithNoActivityIsNotFlaggedAsGaming() {
    LearningEffectSummaryDto summary =
        new LearningEffectSummaryDto(
            0, 0, 0, 0, 0, 0, 0.0, 0.0, 0.0, BigDecimal.ZERO, 0, 0);

    TutorBenchReportDto report = evaluator.evaluate(summary);

    TutorBenchCaseDto antiCheat = caseNamed(report, "anti_cheat_remediation");
    assertThat(antiCheat.passed()).isTrue();
    assertThat(antiCheat.reason()).doesNotContain("刷分风险");
  }

  /** 真正的问题样本：失败了却没有任何补救，必须报出来，且文案要说对是哪一种。 */
  @Test
  void failedChecksWithoutAnyRemediationStillFail() {
    LearningEffectSummaryDto summary =
        new LearningEffectSummaryDto(
            1, 1, 1, 3, 1, 2, 0.333, 1.0, 0.0, new BigDecimal("0.010"), 0, 3);

    TutorBenchReportDto report = evaluator.evaluate(summary);

    TutorBenchCaseDto antiCheat = caseNamed(report, "anti_cheat_remediation");
    assertThat(antiCheat.passed()).isFalse();
    assertThat(antiCheat.reason()).isEqualTo("存在失败微测但未触发任何补救");
  }

  /** 零失败但掌握度超出通过微测应得上限：这才是刷分。 */
  @Test
  void zeroFailuresWithInflatedMasteryDeltaIsFlagged() {
    LearningEffectSummaryDto summary =
        new LearningEffectSummaryDto(
            1, 1, 1, 1, 1, 0, 1.0, 1.0, 0.0, new BigDecimal("0.900"), 0, 1);

    TutorBenchCaseDto antiCheat =
        caseNamed(evaluator.evaluate(summary), "anti_cheat_remediation");

    assertThat(antiCheat.passed()).isFalse();
    assertThat(antiCheat.reason()).contains("刷分风险");
  }

  private static TutorBenchCaseDto caseNamed(TutorBenchReportDto report, String name) {
    return report.cases().stream()
        .filter(item -> name.equals(item.name()))
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
