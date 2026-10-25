package com.pythontopia.ai.runtime;

import com.pythontopia.browse.config.BrowseProperties;
import com.pythontopia.browse.dto.BrowseCitationDto;
import com.pythontopia.learner.evidence.EvidenceGraphMetricsDto;
import com.pythontopia.learner.evidence.EvidenceMemorySnapshotDto;
import com.pythontopia.learner.evidence.LearningEvidenceGraphDto;
import com.pythontopia.learner.evidence.LearningEvidenceGraphService;
import com.pythontopia.learner.evidence.EvidenceMemoryService;
import com.pythontopia.learner.twin.WeeklyReportService;
import com.pythontopia.learner.twin.dto.WeeklyReportDto;
import com.pythontopia.protocol.ProtocolGatewayDescriptorDto;
import com.pythontopia.protocol.ProtocolGatewayService;
import com.pythontopia.protocol.ProtocolSkillPackageDto;
import com.pythontopia.protocol.ProtocolToolDto;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiRuntimeSummaryService {

  private static final int SUMMARY_LIMIT = 160;
  private static final double MIN_GRAPH_COMPLETENESS = 0.45;

  private final ProtocolGatewayService protocolGatewayService;
  private final LearningEvidenceGraphService evidenceGraphService;
  private final EvidenceMemoryService evidenceMemoryService;
  private final WeeklyReportService weeklyReportService;
  private final BrowseProperties browseProperties;
  private final AiRuntimePlatformSnapshotRepository platformSnapshotRepository;

  public AiRuntimeSummaryService(
      ProtocolGatewayService protocolGatewayService,
      LearningEvidenceGraphService evidenceGraphService,
      EvidenceMemoryService evidenceMemoryService,
      WeeklyReportService weeklyReportService,
      BrowseProperties browseProperties,
      AiRuntimePlatformSnapshotRepository platformSnapshotRepository) {
    this.protocolGatewayService = protocolGatewayService;
    this.evidenceGraphService = evidenceGraphService;
    this.evidenceMemoryService = evidenceMemoryService;
    this.weeklyReportService = weeklyReportService;
    this.browseProperties = browseProperties;
    this.platformSnapshotRepository = platformSnapshotRepository;
  }

  @Transactional(readOnly = true)
  public AiRuntimeSummaryDto summaryForUser(long userId) {
    ProtocolGatewayDescriptorDto descriptor = protocolGatewayService.descriptor();
    LearningEvidenceGraphDto graph = evidenceGraphService.buildForUser(userId, null, SUMMARY_LIMIT);
    EvidenceMemorySnapshotDto memory = evidenceMemoryService.createSnapshot(graph);
    WeeklyReportDto twin = weeklyReportService.build(userId);

    AiRuntimeSummaryDto.ProtocolSummary protocol = protocolSummary(descriptor);
    AiRuntimeSummaryDto.EvidenceSummary evidence = evidenceSummary(graph, memory);
    AiRuntimeSummaryDto.LearnerTwinSummary learnerTwin = learnerTwinSummary(twin);
    AiRuntimeSummaryDto.ExternalResearchSummary externalResearch =
        new AiRuntimeSummaryDto.ExternalResearchSummary(
            browseProperties.allowedDomains(),
            BrowseProperties.DEFAULT_MAX_STEPS,
            BrowseCitationDto.EXTERNAL_LABEL);

    return new AiRuntimeSummaryDto(
        protocol,
        evidence,
        learnerTwin,
        externalResearch,
        gates(protocol, evidence, memory, externalResearch));
  }

  /** 查看者本人的闭环运维状态（学生视角）。 */
  @Transactional(readOnly = true)
  public AiRuntimeOperationsDto operationsForUser(long userId) {
    AiRuntimeSummaryDto summary = summaryForUser(userId);
    boolean hasEvidence = summary.evidence().nodeCount() > 0;
    OperationMetrics metrics =
        new OperationMetrics(
            hasEvidence,
            summary.learnerTwin().failedPracticeCount(),
            summary.learnerTwin().resourceRunCount(),
            summary.evidence().touchedKcCount(),
            gateStatus(summary, "protocol.skill_packages"));
    return new AiRuntimeOperationsDto(
        new AiRuntimeOperationsDto.OperationsSubject(
            "SELF", "本人学习证据", 1, hasEvidence ? 1 : 0),
        loopOperations(metrics),
        governanceItems(metrics));
  }

  @Transactional(readOnly = true)
  public AiRuntimeOperationsDto operationsForPlatform() {
    AiRuntimePlatformSnapshotRepository.PlatformSnapshot snapshot =
        platformSnapshotRepository.snapshot();
    AiRuntimeSummaryDto.ProtocolSummary protocol =
        protocolSummary(protocolGatewayService.descriptor());
    OperationMetrics metrics =
        new OperationMetrics(
            snapshot.evidenceLearnerCount() > 0,
            snapshot.failedPracticeCount(),
            snapshot.resourceRunCount(),
            snapshot.touchedKcCount(),
            protocolSkillStatus(protocol));
    return new AiRuntimeOperationsDto(
        new AiRuntimeOperationsDto.OperationsSubject(
            "PLATFORM",
            "全平台学习证据",
            snapshot.activeStudentCount(),
            snapshot.evidenceLearnerCount()),
        loopOperations(metrics),
        governanceItems(metrics));
  }

  private static AiRuntimeSummaryDto.ProtocolSummary protocolSummary(
      ProtocolGatewayDescriptorDto descriptor) {
    List<String> evidenceTools =
        descriptor.mcpTools().stream()
            .filter(tool -> !tool.evidenceFields().isEmpty())
            .map(ProtocolToolDto::name)
            .toList();
    List<String> evidencePackages =
        descriptor.skillPackages().stream()
            .filter(pkg -> !pkg.evidenceFields().isEmpty())
            .map(ProtocolSkillPackageDto::key)
            .toList();
    return new AiRuntimeSummaryDto.ProtocolSummary(
        descriptor.mcpTools().size(),
        evidenceTools.size(),
        descriptor.mcpResources().size(),
        descriptor.aguiEvents().size(),
        descriptor.skillPackages().size(),
        evidencePackages.size(),
        evidenceTools,
        evidencePackages);
  }

  private static AiRuntimeSummaryDto.EvidenceSummary evidenceSummary(
      LearningEvidenceGraphDto graph, EvidenceMemorySnapshotDto memory) {
    EvidenceGraphMetricsDto metrics = graph.metrics();
    return new AiRuntimeSummaryDto.EvidenceSummary(
        metrics.nodeCount(),
        metrics.edgeCount(),
        metrics.touchedKcCount(),
        metrics.groundedPageCount(),
        metrics.checkCoverageRate(),
        metrics.graphCompletenessScore(),
        memory.graphMemoryScore(),
        memory.directSignalCount(),
        memory.propagatedSignalCount(),
        memory.retainedEvidenceCount());
  }

  private static AiRuntimeSummaryDto.LearnerTwinSummary learnerTwinSummary(WeeklyReportDto report) {
    return new AiRuntimeSummaryDto.LearnerTwinSummary(
        report.memoryBrief().totalEvents(),
        report.memoryBrief().failedPractices(),
        report.memoryBrief().succeededPractices(),
        report.insights().size(),
        report.masteryDeltas().size(),
        report.resourceRuns().size());
  }

  private static List<AiRuntimeSummaryDto.RuntimeGate> gates(
      AiRuntimeSummaryDto.ProtocolSummary protocol,
      AiRuntimeSummaryDto.EvidenceSummary evidence,
      EvidenceMemorySnapshotDto memory,
      AiRuntimeSummaryDto.ExternalResearchSummary externalResearch) {
    List<AiRuntimeSummaryDto.RuntimeGate> gates = new ArrayList<>();
    gates.add(
        new AiRuntimeSummaryDto.RuntimeGate(
            "protocol.evidence_fields",
            "MCP 工具声明证据字段",
            protocol.toolsWithEvidenceFieldCount() >= 2 ? "PASS" : "WARN",
            protocol.toolsWithEvidenceFieldCount(),
            2.0,
            protocol.toolsWithEvidenceFields()));
    gates.add(
        new AiRuntimeSummaryDto.RuntimeGate(
            "protocol.skill_packages",
            "AI 能力包声明证据合同",
            protocol.evidenceSkillPackageCount() >= 3 ? "PASS" : "WARN",
            protocol.evidenceSkillPackageCount(),
            3.0,
            protocol.evidenceSkillPackages()));
    gates.add(
        new AiRuntimeSummaryDto.RuntimeGate(
            "evidence.graph_completeness",
            "学习证据图完整度",
            evidence.nodeCount() == 0
                ? "INSUFFICIENT_EVIDENCE"
                : evidence.graphCompletenessScore() >= MIN_GRAPH_COMPLETENESS ? "PASS" : "WARN",
            evidence.graphCompletenessScore(),
            MIN_GRAPH_COMPLETENESS,
            List.of("learner.evidence.graph")));
    gates.add(
        new AiRuntimeSummaryDto.RuntimeGate(
            "memory.graph_over_naive",
            "Evidence Memory 优于朴素近期事件",
            memory.retainedEvidenceCount() == 0
                ? "INSUFFICIENT_EVIDENCE"
                : memory.graphMemoryScore() >= memory.naiveMemoryScore() ? "PASS" : "WARN",
            memory.graphMemoryScore() - memory.naiveMemoryScore(),
            0.0,
            memory.signals().stream()
                .limit(4)
                .map(signal -> signal.kcCode() + ":" + signal.signalKind())
                .toList()));
    gates.add(
        new AiRuntimeSummaryDto.RuntimeGate(
            "browse.external_citation_policy",
            "联网查证外部来源隔离",
            externalResearch.allowedDomains().isEmpty() ? "WARN" : "PASS",
            externalResearch.allowedDomains().size(),
            1.0,
            externalResearch.allowedDomains()));
    return gates;
  }

  private static List<AiRuntimeOperationsDto.LoopOperation> loopOperations(
      OperationMetrics metrics) {
    return List.of(
        new AiRuntimeOperationsDto.LoopOperation(
            "LEARNER",
            "学生学习闭环",
            metrics.hasEvidence() ? "WARN" : "INSUFFICIENT_EVIDENCE",
            "",
            "open:student-learning-loop",
            List.of("learner.evidence.graph")),
        new AiRuntimeOperationsDto.LoopOperation(
            "DEBUG",
            "代码调试闭环",
            debugStatus(metrics),
            metrics.failedPracticeCount() > 0 ? "bug-mine-errors" : "",
            "open:bug-mine-or-transfer",
            metrics.failedPracticeCount() > 0 ? List.of("bugmine.errorbook") : List.of()),
        new AiRuntimeOperationsDto.LoopOperation(
            "RESOURCE",
            "资源生成闭环",
            metrics.resourceRunCount() > 0 ? "WARN" : "INSUFFICIENT_EVIDENCE",
            "",
            "evaluate:resource_feedback_or_check",
            metrics.resourceRunCount() > 0
                ? List.of("resource_feedback_or_check")
                : List.of()),
        new AiRuntimeOperationsDto.LoopOperation(
            "WORLD",
            "小镇世界闭环",
            metrics.touchedKcCount() > 0 ? "WARN" : "INSUFFICIENT_EVIDENCE",
            "",
            "review:town_hub-world-patch",
            metrics.touchedKcCount() > 0 ? List.of("world-loop-proposal") : List.of()),
        new AiRuntimeOperationsDto.LoopOperation(
            "IMPROVE",
            "系统演化闭环",
            metrics.improveStatus(),
            "agentic-m3-system-evolution-v1",
            "review:policy-canary-dry-run",
            List.of("agentic-m3-system-evolution-v1")));
  }

  private static List<AiRuntimeOperationsDto.GovernanceItem> governanceItems(
      OperationMetrics metrics) {
    return List.of(
        new AiRuntimeOperationsDto.GovernanceItem(
            "resource_review",
            "资源发布审查",
            metrics.resourceRunCount() > 0 ? "WARN" : "INSUFFICIENT_EVIDENCE",
            metrics.resourceRunCount() > 0
                ? "检测到资源生成活动，待核验发布审查证据"
                : "暂无资源生成活动或审查证据",
            "/workshop",
            List.of()),
        new AiRuntimeOperationsDto.GovernanceItem(
            "world_patch_review",
            "世界补丁审查",
            metrics.touchedKcCount() > 0 ? "WARN" : "INSUFFICIENT_EVIDENCE",
            metrics.touchedKcCount() > 0
                ? "检测到知识点触达活动，待核验世界补丁与回滚审查"
                : "暂无世界变更活动或审查证据",
            "",
            List.of()),
        new AiRuntimeOperationsDto.GovernanceItem(
            "policy_canary_review",
            "策略灰度审核",
            metrics.improveStatus(),
            "查看 canary dry-run 与 rollback 状态",
            "",
            List.of("agentic-m3-system-evolution-v1")));
  }

  private static String debugStatus(OperationMetrics metrics) {
    if (metrics.failedPracticeCount() > 0) {
      return "WARN";
    }
    return metrics.hasEvidence() ? "PASS" : "INSUFFICIENT_EVIDENCE";
  }

  private static String protocolSkillStatus(AiRuntimeSummaryDto.ProtocolSummary protocol) {
    return protocol.evidenceSkillPackageCount() >= 3 ? "PASS" : "WARN";
  }

  private static String gateStatus(AiRuntimeSummaryDto summary, String key) {
    return summary.gates().stream()
        .filter(gate -> key.equals(gate.key()))
        .map(AiRuntimeSummaryDto.RuntimeGate::status)
        .findFirst()
        .orElse("INSUFFICIENT_EVIDENCE");
  }

  private record OperationMetrics(
      boolean hasEvidence,
      int failedPracticeCount,
      int resourceRunCount,
      int touchedKcCount,
      String improveStatus) {}
}
