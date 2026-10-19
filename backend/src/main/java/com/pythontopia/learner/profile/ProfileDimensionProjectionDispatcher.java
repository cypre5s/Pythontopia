package com.pythontopia.learner.profile;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class ProfileDimensionProjectionDispatcher {

  private static final Logger log = LoggerFactory.getLogger(ProfileDimensionProjectionDispatcher.class);
  private static final int MAX_BATCH_SIZE = 100;
  private static final int MAX_ATTEMPTS = 8;
  private static final int BASE_BACKOFF_SECONDS = 5;
  private static final int MAX_BACKOFF_SECONDS = 300;

  private final ProfileDimensionProjectionOutboxRepository repository;
  private final ProfileDimensionProjectionProcessor processor;

  ProfileDimensionProjectionDispatcher(
      ProfileDimensionProjectionOutboxRepository repository,
      ProfileDimensionProjectionProcessor processor) {
    this.repository = repository;
    this.processor = processor;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onProjectionQueued(ProfileDimensionProjectionQueued event) {
    dispatchOne(event.outboxId());
  }

  @Scheduled(fixedDelayString = "${pythontopia.profile-projection.poll-ms:5000}", initialDelay = 5000)
  public void retryPending() {
    List<Long> ids = repository.findDuePendingIds(MAX_BATCH_SIZE);
    ids.forEach(this::dispatchOne);
  }

  void dispatchOne(long id) {
    try {
      processor.process(id);
    } catch (RuntimeException ex) {
      String errorKind = ex.getClass().getSimpleName();
      if (errorKind.isBlank()) {
        errorKind = "RuntimeException";
      }
      ProfileDimensionProjectionOutboxRepository.Failure failure =
          repository.recordFailure(
              id,
              errorKind,
              MAX_ATTEMPTS,
              BASE_BACKOFF_SECONDS,
              MAX_BACKOFF_SECONDS);
      if (failure == null) {
        return;
      }
      if ("FAILED".equals(failure.status())) {
        log.error(
            "profile projection quarantined after retries outboxId={} attempts={} errorType={}",
            id,
            failure.attemptCount(),
            errorKind);
      } else {
        log.warn(
            "profile projection deferred for retry outboxId={} attempt={} errorType={}",
            id,
            failure.attemptCount(),
            errorKind);
      }
    }
  }
}
