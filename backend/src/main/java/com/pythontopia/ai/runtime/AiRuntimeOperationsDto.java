package com.pythontopia.ai.runtime;

import java.util.List;

public record AiRuntimeOperationsDto(
    List<LoopOperation> loops, List<GovernanceItem> governance) {

  public AiRuntimeOperationsDto {
    loops = loops == null ? List.of() : List.copyOf(loops);
    governance = governance == null ? List.of() : List.copyOf(governance);
  }

  public record LoopOperation(
      String loopType,
      String label,
      String status,
      String latestTraceId,
      String nextAction,
      List<String> evidenceRefs) {

    public LoopOperation {
      evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
    }
  }

  public record GovernanceItem(
      String key,
      String label,
      String status,
      String action,
      String reviewRoute,
      List<String> evidenceRefs) {

    public GovernanceItem {
      evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
    }
  }
}
