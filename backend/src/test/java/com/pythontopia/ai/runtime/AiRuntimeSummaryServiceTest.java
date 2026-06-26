package com.pythontopia.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pythontopia.browse.config.BrowseProperties;
import com.pythontopia.learner.evidence.EvidenceGraphMetricsDto;
import com.pythontopia.learner.evidence.EvidenceMemoryService;
import com.pythontopia.learner.evidence.EvidenceMemorySignalDto;
import com.pythontopia.learner.evidence.EvidenceMemorySnapshotDto;
import com.pythontopia.learner.evidence.LearningEvidenceGraphDto;
import com.pythontopia.learner.evidence.LearningEvidenceGraphService;
import com.pythontopia.learner.twin.WeeklyReportService;
import com.pythontopia.learner.twin.dto.WeeklyReportDto;
import com.pythontopia.protocol.ProtocolGatewayService;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiRuntimeSummaryServiceTest {

  private final ProtocolGatewayService protocolGatewayService = new ProtocolGatewayService();
  private final LearningEvidenceGraphService evidenceGraphService =
      mock(LearningEvidenceGraphService.class);
  private final EvidenceMemoryService evidenceMemoryService = mock(EvidenceMemoryService.class);
  private final WeeklyReportService weeklyReportService = mock(WeeklyReportService.class);
  private final BrowseProperties browseProperties =
      new BrowseProperties(List.of("judge.pythontopia.local"));
  private final AiRuntimeSummaryService service =
      new AiRuntimeSummaryService(
          protocolGatewayService,
          evidenceGraphService,
          evidenceMemoryService,
          weeklyReportService,
          browseProperties);

  @Test
  void summaryCombinesProtocolEvidenceMemoryTwinAndBrowseSafetyWall() {
    long userId = 7L;
    when(evidenceGraphService.buildForUser(eq(userId), eq(null), eq(160)))
        .thenReturn(
            new LearningEvidenceGraphDto(
                userId,
                null,
                List.of(),
                List.of(),
                new EvidenceGraphMetricsDto(18, 24, 4, 5, 0.75, 0.5, 0.8, 0.25, 0.66, 1),
                List.of()));
    when(evidenceMemoryService.createSnapshot(eq(userId), eq(null), eq(160), eq(false)))
        .thenReturn(
            new EvidenceMemorySnapshotDto(
                null,
                userId,
                null,
                2,
                3,
                6,
                0.82,
                0.3,
                List.of(
                    new EvidenceMemorySignalDto(
                        "PY43-KC-001",
                        "DIRECT",
                        1.2,
                        0.0,
                        1.2,
                        null,
                        List.of(11L),
                        List.of(21L),
                        List.of(31L),
                        "retain",
                        "resource check failed"))));
    when(weeklyReportService.build(eq(userId)))
        .thenReturn(
            new WeeklyReportDto(
                "2026-06-19",
                "2026-06-26",
                new WeeklyReportDto.MemoryBrief(
                    9, 3, 4, List.of("PY43-KC-001", "PY43-KC-002"), List.of("循环边界仍需复盘")),
                List.of(
                    new WeeklyReportDto.InsightBrief(
                        1L, "更适合先例子后抽象", "cognitive_style", List.of("visual"), "2026-06-25")),
                List.of(
                    new WeeklyReportDto.MasteryDeltaBrief(
                        "PY43-KC-001", 0.62, 0.4, List.of("resource_check:21;resource:5"))),
                List.of(
                    new WeeklyReportDto.ResourceRunBrief(
                        "ResourceDesignerAgent", "讲义", "SUCCESS", 3, "2026-06-25T10:00:00Z"))));

    AiRuntimeSummaryDto summary = service.summaryForUser(userId);

    assertThat(summary.protocol().mcpToolCount()).isGreaterThanOrEqualTo(6);
    assertThat(summary.protocol().toolsWithEvidenceFields())
        .contains("workshop.resource.check.submit", "learner.recommendations.read");
    assertThat(summary.evidence().nodeCount()).isEqualTo(18);
    assertThat(summary.evidence().directSignalCount()).isEqualTo(2);
    assertThat(summary.evidence().graphMemoryScore()).isEqualTo(0.82);
    assertThat(summary.learnerTwin().memoryEventCount()).isEqualTo(9);
    assertThat(summary.learnerTwin().resourceRunCount()).isEqualTo(1);
    assertThat(summary.externalResearch().allowedDomains())
        .contains("docs.python.org", "peps.python.org", "pypi.org", "judge.pythontopia.local");
    assertThat(summary.externalResearch().citationPolicy()).isEqualTo("外部内容，非课件");
    assertThat(summary.gates()).extracting(AiRuntimeSummaryDto.RuntimeGate::key)
        .contains(
            "protocol.evidence_fields",
            "evidence.graph_completeness",
            "memory.graph_over_naive",
            "browse.external_citation_policy");
    assertThat(summary.gates()).filteredOn(gate -> "memory.graph_over_naive".equals(gate.key()))
        .first()
        .extracting(AiRuntimeSummaryDto.RuntimeGate::status)
        .isEqualTo("PASS");
  }

  @Test
  void emptyLearnerTraceIsMarkedInsufficientInsteadOfPretendingToPass() {
    long userId = 8L;
    when(evidenceGraphService.buildForUser(eq(userId), eq(null), eq(160)))
        .thenReturn(
            new LearningEvidenceGraphDto(
                userId,
                null,
                List.of(),
                List.of(),
                new EvidenceGraphMetricsDto(0, 0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0),
                List.of()));
    when(evidenceMemoryService.createSnapshot(eq(userId), eq(null), eq(160), eq(false)))
        .thenReturn(new EvidenceMemorySnapshotDto(null, userId, null, 0, 0, 0, 0.0, 0.0, List.of()));
    when(weeklyReportService.build(eq(userId)))
        .thenReturn(
            new WeeklyReportDto(
                "2026-06-19",
                "2026-06-26",
                new WeeklyReportDto.MemoryBrief(0, 0, 0, List.of(), List.of()),
                List.of(),
                List.of(),
                List.of()));

    AiRuntimeSummaryDto summary = service.summaryForUser(userId);

    assertThat(summary.gates())
        .filteredOn(gate -> "evidence.graph_completeness".equals(gate.key()))
        .first()
        .extracting(AiRuntimeSummaryDto.RuntimeGate::status)
        .isEqualTo("INSUFFICIENT_EVIDENCE");
    assertThat(summary.gates())
        .filteredOn(gate -> "memory.graph_over_naive".equals(gate.key()))
        .first()
        .extracting(AiRuntimeSummaryDto.RuntimeGate::status)
        .isEqualTo("INSUFFICIENT_EVIDENCE");
  }
}
