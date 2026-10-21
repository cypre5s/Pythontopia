package com.pythontopia.world.boss;

import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.world.boss.dto.BossAttemptResultDto;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Completes the expedition reward ledger and judged response as one database transaction. */
@Service
public class ExpeditionAttemptCompletionService {

  private final ExpeditionScoreService expeditionScoreService;
  private final BossAttemptIdempotencyService attemptIdempotencyService;
  private final ApplicationEventPublisher eventPublisher;

  public ExpeditionAttemptCompletionService(
      ExpeditionScoreService expeditionScoreService,
      BossAttemptIdempotencyService attemptIdempotencyService,
      ApplicationEventPublisher eventPublisher) {
    this.expeditionScoreService = expeditionScoreService;
    this.attemptIdempotencyService = attemptIdempotencyService;
    this.eventPublisher = eventPublisher;
  }

  @Transactional
  public Completion complete(
      Long userId,
      String runId,
      String nodeId,
      Integer bossPhase,
      String problemId,
      String questionFingerprint,
      boolean passed,
      int score,
      UUID attemptId,
      BossAttemptIdempotencyService.Claim claim,
      BossAttemptResultDto response,
      LearningEvent event) {
    ExpeditionScoreService.VerifiedAttemptResult attempt =
        expeditionScoreService.recordVerifiedAttempt(
            userId,
            runId,
            nodeId,
            bossPhase,
            problemId,
            questionFingerprint,
            passed,
            score);
    if (!attempt.accepted()) {
      return new Completion(false, null);
    }

    LearningEvent acceptedEvent = attempt.publishLearningEvent() ? event : null;
    BossAttemptResultDto completedResponse;
    if (claim != null) {
      completedResponse =
          attemptIdempotencyService.complete(userId, attemptId, claim, response, acceptedEvent);
    } else {
      if (acceptedEvent != null) {
        eventPublisher.publishEvent(acceptedEvent);
      }
      completedResponse = response;
    }
    return new Completion(true, completedResponse);
  }

  public record Completion(boolean accepted, BossAttemptResultDto response) {}
}
