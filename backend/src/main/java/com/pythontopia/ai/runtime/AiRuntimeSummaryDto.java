package com.pythontopia.ai.runtime;

import java.util.List;

public record AiRuntimeSummaryDto(
    ProtocolSummary protocol,
    EvidenceSummary evidence,
    LearnerTwinSummary learnerTwin,
    ExternalResearchSummary externalResearch,
    List<RuntimeGate> gates) {

  public AiRuntimeSummaryDto {
    gates = gates == null ? List.of() : List.copyOf(gates);
  }

  public record ProtocolSummary(
      int mcpToolCount,
      int toolsWithEvidenceFieldCount,
      int mcpResourceCount,
      int aguiEventCount,
      List<String> toolsWithEvidenceFields) {

    public ProtocolSummary {
      toolsWithEvidenceFields =
          toolsWithEvidenceFields == null ? List.of() : List.copyOf(toolsWithEvidenceFields);
    }
  }

  public record EvidenceSummary(
      int nodeCount,
      int edgeCount,
      int touchedKcCount,
      int groundedPageCount,
      double checkCoverageRate,
      double graphCompletenessScore,
      double graphMemoryScore,
      int directSignalCount,
      int propagatedSignalCount,
      int retainedEvidenceCount) {}

  public record LearnerTwinSummary(
      int memoryEventCount,
      int failedPracticeCount,
      int succeededPracticeCount,
      int insightCount,
      int masteryKcCount,
      int resourceRunCount) {}

  public record ExternalResearchSummary(
      List<String> allowedDomains, int defaultMaxSteps, String citationPolicy) {

    public ExternalResearchSummary {
      allowedDomains = allowedDomains == null ? List.of() : List.copyOf(allowedDomains);
    }
  }

  public record RuntimeGate(
      String key,
      String label,
      String status,
      double value,
      double threshold,
      List<String> evidenceRefs) {

    public RuntimeGate {
      evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
    }
  }
}
