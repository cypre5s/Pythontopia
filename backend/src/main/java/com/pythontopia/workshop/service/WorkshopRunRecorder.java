package com.pythontopia.workshop.service;

import com.pythontopia.agent.run.ResourceRunRecorder;
import com.pythontopia.agent.run.RunStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link ResourceRunRecorder} 的薄封装：在新事务（REQUIRES_NEW）中记录运行，
 * 确保失败的工坊运行即便父事务回滚也仍落库可审计。
 */
@Component
public class WorkshopRunRecorder {

  private final ResourceRunRecorder delegate;

  public WorkshopRunRecorder(ResourceRunRecorder delegate) {
    this.delegate = delegate;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Long recordInNewTransaction(
      Long userId,
      String agentName,
      String scenario,
      Object inputSummary,
      Object outputArtifact,
      List<Long> evidencePageIds,
      Object safetyResult,
      Object groundingResult,
      Object graphTrace,
      UUID correlationId,
      RunStatus status) {
    return delegate.record(
        userId,
        agentName,
        scenario,
        inputSummary,
        outputArtifact,
        evidencePageIds,
        safetyResult,
        groundingResult,
        graphTrace,
        correlationId,
        status);
  }
}
