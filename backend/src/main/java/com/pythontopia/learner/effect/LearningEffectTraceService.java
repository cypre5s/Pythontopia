package com.pythontopia.learner.effect;

import com.pythontopia.learner.evidence.LearningEvidenceGraphDto;
import com.pythontopia.learner.evidence.LearningEvidenceGraphService;
import org.springframework.stereotype.Service;

@Service
public class LearningEffectTraceService {

  private final LearningEffectTraceRepository repository;
  private final LearningEvidenceGraphService evidenceGraphService;

  public LearningEffectTraceService(
      LearningEffectTraceRepository repository, LearningEvidenceGraphService evidenceGraphService) {
    this.repository = repository;
    this.evidenceGraphService = evidenceGraphService;
  }

  public LearningEffectSummaryDto summary(Long userId) {
    LearningEffectSummaryDto summary = repository.summary(userId);
    LearningEvidenceGraphDto graph = evidenceGraphService.buildForUser(userId, null, 120);
    return summary.withEvidenceGraph(graph.metrics(), graph.personalizationSignals().size());
  }
}
