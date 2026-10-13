package com.pythontopia.learner.profile;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ProfileDimensionProjectionOutboxService {

  private final ProfileDimensionProjectionOutboxRepository repository;
  private final ApplicationEventPublisher eventPublisher;

  ProfileDimensionProjectionOutboxService(
      ProfileDimensionProjectionOutboxRepository repository,
      ApplicationEventPublisher eventPublisher) {
    this.repository = repository;
    this.eventPublisher = eventPublisher;
  }

  @Transactional
  public void enqueue(ProfileDimensionProjection projection) {
    long outboxId = repository.enqueue(projection);
    eventPublisher.publishEvent(new ProfileDimensionProjectionQueued(outboxId));
  }
}
