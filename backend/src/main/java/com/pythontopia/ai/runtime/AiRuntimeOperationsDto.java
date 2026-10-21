package com.pythontopia.ai.runtime;

import java.util.List;
import java.util.Objects;

/**
 * 闭环运维/治理面板数据。
 *
 * <p>{@code subject} 是<strong>必读</strong>字段：所有 loop / governance 状态都由某一个学习者的
 * 学习证据图算出，没有它就无法判断这块面板到底在描述谁。历史上教师端拿查看者自己的 userId
 * 建图，教师没有学习事件 → 全部 INSUFFICIENT_EVIDENCE，被误读成「平台没有证据」。
 */
public record AiRuntimeOperationsDto(
    OperationsSubject subject, List<LoopOperation> loops, List<GovernanceItem> governance) {

  public AiRuntimeOperationsDto {
    subject = Objects.requireNonNull(subject, "subject");
    loops = loops == null ? List.of() : List.copyOf(loops);
    governance = governance == null ? List.of() : List.copyOf(governance);
  }

  public record OperationsSubject(
      String scope, String label, int learnerCount, int evidenceLearnerCount) {}

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
