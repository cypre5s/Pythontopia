package com.pythontopia.world.boss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.world.boss.dto.BossAttemptResultDto;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ExpeditionAttemptCompletionServiceTest {

  private static final Long USER_ID = 7L;
  private static final String RUN_ID = "123e4567-e89b-12d3-a456-426614174000";
  private static final String NODE_ID = "L0-0";
  private static final String PROBLEM_ID = "issue-1";
  private static final String FINGERPRINT = "a".repeat(64);

  @Mock ExpeditionScoreService expeditionScoreService;
  @Mock BossAttemptIdempotencyService attemptIdempotencyService;
  @Mock ApplicationEventPublisher eventPublisher;

  private ExpeditionAttemptCompletionService service;
  private final UUID attemptId = UUID.randomUUID();
  private final BossAttemptIdempotencyService.Claim claim =
      new BossAttemptIdempotencyService.Claim(UUID.randomUUID(), null, "b".repeat(64));
  private final BossAttemptResultDto response =
      new BossAttemptResultDto(PROBLEM_ID, true, 1, 1, null, 100, "PASS");
  private final LearningEvent event =
      new LearningEvent.PracticeSucceeded(USER_ID, "PY43-KC-001", null, "easy");

  @BeforeEach
  void setUp() {
    service =
        new ExpeditionAttemptCompletionService(
            expeditionScoreService, attemptIdempotencyService, eventPublisher);
  }

  @Test
  void storesTheResponseAndPublishesOnlyWhenTheLedgerAcceptsANewLearningEvent() {
    when(expeditionScoreService.recordVerifiedAttempt(
            USER_ID, RUN_ID, NODE_ID, null, PROBLEM_ID, FINGERPRINT, true, 100))
        .thenReturn(new ExpeditionScoreService.VerifiedAttemptResult(true, true));
    when(attemptIdempotencyService.complete(USER_ID, attemptId, claim, response, event))
        .thenReturn(response);

    var completed =
        service.complete(
            USER_ID,
            RUN_ID,
            NODE_ID,
            null,
            PROBLEM_ID,
            FINGERPRINT,
            true,
            100,
            attemptId,
            claim,
            response,
            event);

    assertThat(completed.accepted()).isTrue();
    assertThat(completed.response()).isEqualTo(response);
    verify(attemptIdempotencyService).complete(USER_ID, attemptId, claim, response, event);
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  void storesDuplicateVerdictsWithoutPublishingAnotherLearningEvent() {
    when(expeditionScoreService.recordVerifiedAttempt(
            USER_ID, RUN_ID, NODE_ID, null, PROBLEM_ID, FINGERPRINT, true, 100))
        .thenReturn(new ExpeditionScoreService.VerifiedAttemptResult(true, false));
    when(attemptIdempotencyService.complete(USER_ID, attemptId, claim, response, null))
        .thenReturn(response);

    var completed =
        service.complete(
            USER_ID,
            RUN_ID,
            NODE_ID,
            null,
            PROBLEM_ID,
            FINGERPRINT,
            true,
            100,
            attemptId,
            claim,
            response,
            event);

    assertThat(completed.accepted()).isTrue();
    verify(attemptIdempotencyService).complete(USER_ID, attemptId, claim, response, null);
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  void doesNotCompleteAnIdempotencyClaimWhenTheAttemptWasRejected() {
    when(expeditionScoreService.recordVerifiedAttempt(
            USER_ID, RUN_ID, NODE_ID, null, PROBLEM_ID, FINGERPRINT, true, 100))
        .thenReturn(new ExpeditionScoreService.VerifiedAttemptResult(false, false));

    var completed =
        service.complete(
            USER_ID,
            RUN_ID,
            NODE_ID,
            null,
            PROBLEM_ID,
            FINGERPRINT,
            true,
            100,
            attemptId,
            claim,
            response,
            event);

    assertThat(completed.accepted()).isFalse();
    assertThat(completed.response()).isNull();
    verify(attemptIdempotencyService, never()).complete(eq(USER_ID), eq(attemptId), eq(claim), any(), any());
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  void publishesAnAcceptedEventWhenThereIsNoIdempotencyClaim() {
    when(expeditionScoreService.recordVerifiedAttempt(
            USER_ID, RUN_ID, NODE_ID, null, PROBLEM_ID, FINGERPRINT, true, 100))
        .thenReturn(new ExpeditionScoreService.VerifiedAttemptResult(true, true));

    var completed =
        service.complete(
            USER_ID,
            RUN_ID,
            NODE_ID,
            null,
            PROBLEM_ID,
            FINGERPRINT,
            true,
            100,
            null,
            null,
            response,
            event);

    assertThat(completed.response()).isEqualTo(response);
    verify(eventPublisher).publishEvent(event);
    verify(attemptIdempotencyService, never()).complete(any(), any(), any(), any(), any());
  }
}
