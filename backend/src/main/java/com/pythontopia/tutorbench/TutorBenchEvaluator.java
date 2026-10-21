package com.pythontopia.tutorbench;

import com.pythontopia.learner.effect.LearningEffectSummaryDto;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TutorBenchEvaluator {

  public TutorBenchReportDto evaluate(LearningEffectSummaryDto summary) {
    List<TutorBenchCaseDto> cases = new ArrayList<>();
    cases.add(multiTurnLearningClosure(summary));
    cases.add(antiCheatRemediation(summary));
    cases.add(evidenceConsistency(summary));
    cases.add(evidenceGraphConnectivity(summary));
    cases.add(groundedPersonalization(summary));
    cases.add(trajectoryTraceability(summary));
    int passed = (int) cases.stream().filter(TutorBenchCaseDto::passed).count();
    return new TutorBenchReportDto(
        cases.size(), passed, ratio(passed, cases.size()), summary, List.copyOf(cases));
  }

  private TutorBenchCaseDto multiTurnLearningClosure(LearningEffectSummaryDto summary) {
    boolean passed =
        summary.resourceCount() > 0
            && summary.consumedResourceCount() > 0
            && summary.checkedResourceCount() >= summary.consumedResourceCount()
            && summary.checkMasteryEvents() > 0;
    double score =
        average(
            summary.resourceCount() > 0,
            summary.consumedResourceCount() > 0,
            summary.checkedResourceCount() >= summary.consumedResourceCount(),
            summary.checkMasteryEvents() > 0);
    return new TutorBenchCaseDto(
        "multi_turn_learning_closure",
        passed,
        score,
        passed
            ? "资源生成、消费、微测、掌握度事件已串联"
            : "缺少资源消费后的微测或掌握度事件");
  }

  /**
   * 反作弊：掌握度增长不得超过通过微测应得的上限；失败微测必须触发补救。
   *
   * <p>「从未失败过微测」与「失败了但没补救」是两种<strong>完全不同</strong>的结论：前者是零失败的
   * 优等生或全新零活跃用户，补救率不适用（N/A），只需检查有没有刷分；把它和后者一样报成
   * 「失败微测未触发补救或存在刷分风险」是断言方向与命名相反的误报。
   */
  private TutorBenchCaseDto antiCheatRemediation(LearningEffectSummaryDto summary) {
    boolean noFailedChecks = summary.failedCheckCount() == 0;
    boolean remediationSatisfied = noFailedChecks || summary.remediationRate() > 0.0;
    boolean masteryDeltaIsBounded =
        summary.totalMasteryDelta().doubleValue() < summary.passedCheckCount() * 0.05 + 0.001;
    boolean passed = remediationSatisfied && masteryDeltaIsBounded;
    double score = average(remediationSatisfied, masteryDeltaIsBounded);
    String reason;
    if (passed && noFailedChecks) {
      reason = "本期无失败微测，补救指标不适用，且掌握度增量与通过证据一致";
    } else if (passed) {
      reason = "失败微测没有正向刷分，并触发补救";
    } else if (!masteryDeltaIsBounded) {
      reason = "掌握度增量超过通过微测可支撑范围，存在刷分风险";
    } else {
      reason = "失败微测未触发补救";
    }
    return new TutorBenchCaseDto(
        "anti_cheat_remediation",
        passed,
        score,
        reason);
  }

  private TutorBenchCaseDto evidenceConsistency(LearningEffectSummaryDto summary) {
    if (summary.positiveResourceReadEvents() > 0) {
      return new TutorBenchCaseDto(
          "evidence_consistency",
          false,
          0.0,
          "存在 RESOURCE_READ 直接加分，资源消费绕过了微测证据");
    }
    boolean passed =
        summary.positiveResourceReadEvents() == 0
            && summary.checkCount() == summary.checkMasteryEvents()
            && summary.evidenceCompletionRate() >= 1.0;
    double score =
        average(
            summary.positiveResourceReadEvents() == 0,
            summary.checkCount() == summary.checkMasteryEvents(),
            summary.evidenceCompletionRate() >= 1.0);
    return new TutorBenchCaseDto(
        "evidence_consistency",
        passed,
        score,
        passed
            ? "掌握度事件全部由微测/操作证据支撑"
            : "存在 RESOURCE_READ 直接加分、缺失微测事件或证据未闭合");
  }

  private TutorBenchCaseDto evidenceGraphConnectivity(LearningEffectSummaryDto summary) {
    var metrics = summary.evidenceGraphMetrics();
    boolean passed =
        metrics.nodeCount() > 0
            && metrics.edgeCount() > 0
            && metrics.connectedEvidenceRate() >= 0.8
            && metrics.graphCompletenessScore() >= 0.6;
    double score =
        average(
            metrics.nodeCount() > 0,
            metrics.edgeCount() > 0,
            metrics.connectedEvidenceRate() >= 0.8,
            metrics.graphCompletenessScore() >= 0.6);
    return new TutorBenchCaseDto(
        "evidence_graph_connectivity",
        passed,
        score,
        passed ? "学习证据图已形成闭合子图" : "学习证据仍像散点，缺少可追踪连接");
  }

  private TutorBenchCaseDto groundedPersonalization(LearningEffectSummaryDto summary) {
    var metrics = summary.evidenceGraphMetrics();
    boolean passed = summary.personalizationSignalCount() > 0 && metrics.groundedPageCount() > 0;
    double score = average(summary.personalizationSignalCount() > 0, metrics.groundedPageCount() > 0);
    return new TutorBenchCaseDto(
        "grounded_personalization",
        passed,
        score,
        passed ? "个性化信号能追溯到真实学习证据与课件页" : "个性化推荐缺少事件或页码支撑");
  }

  private TutorBenchCaseDto trajectoryTraceability(LearningEffectSummaryDto summary) {
    var metrics = summary.evidenceGraphMetrics();
    boolean passed =
        metrics.touchedKcCount() > 0
            && metrics.groundedPageCount() > 0
            && metrics.checkCoverageRate() >= 1.0
            && metrics.masteryEvidenceRate() >= 1.0;
    double score =
        average(
            metrics.touchedKcCount() > 0,
            metrics.groundedPageCount() > 0,
            metrics.checkCoverageRate() >= 1.0,
            metrics.masteryEvidenceRate() >= 1.0);
    return new TutorBenchCaseDto(
        "trajectory_traceability",
        passed,
        score,
        passed ? "资源、检查、页码与掌握度轨迹可互相追溯" : "轨迹缺页码、检查或掌握度证据");
  }

  private static double average(boolean... checks) {
    int passed = 0;
    for (boolean check : checks) {
      if (check) {
        passed++;
      }
    }
    return ratio(passed, checks.length);
  }

  private static double ratio(int numerator, int denominator) {
    if (denominator <= 0) {
      return 0.0;
    }
    return Math.round(((double) numerator / (double) denominator) * 1000.0) / 1000.0;
  }
}
