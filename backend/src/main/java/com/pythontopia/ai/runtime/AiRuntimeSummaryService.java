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

  public AiRuntimeSummaryService(
      ProtocolGatewayService protocolGatewayService,
      LearningEvidenceGraphService evidenceGraphService,
      EvidenceMemoryService evidenceMemoryService,
      WeeklyReportService weeklyReportService,
      BrowseProperties browseProperties) {
    this.protocolGatewayService = protocolGatewayService;
    this.evidenceGraphService = evidenceGraphService;
    this.evidenceMemoryService = evidenceMemoryService;
    this.weeklyReportService = weeklyReportService;
    this.browseProperties = browseProperties;
  }

  @Transactional(readOnly = true)
  public AiRuntimeSummaryDto summaryForUser(long userId) {
    ProtocolGatewayDescriptorDto descriptor = protocolGatewayService.descriptor();
    LearningEvidenceGraphDto graph = evidenceGraphService.buildForUser(userId, null, SUMMARY_LIMIT);
    EvidenceMemorySnapshotDto memory =
        evidenceMemoryService.createSnapshot(userId, null, SUMMARY_LIMIT, false);
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

  private static AiRuntimeSummaryDto.ProtocolSummary protocolSummary(
      ProtocolGatewayDescriptorDto descriptor) {
    List<String> evidenceTools =
        descriptor.mcpTools().stream()
            .filter(tool -> !tool.evidenceFields().isEmpty())
            .map(ProtocolToolDto::name)
            .toList();
    return new AiRuntimeSummaryDto.ProtocolSummary(
        descriptor.mcpTools().size(),
        evidenceTools.size(),
        descriptor.mcpResources().size(),
        descriptor.aguiEvents().size(),
        evidenceTools);
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
}
