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
      repository.recordFailure(id, errorKind);
      log.warn("profile projection deferred for retry outboxId={} error={}", id, errorKind);
    }
  }
}
