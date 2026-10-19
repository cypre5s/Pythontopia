package com.pythontopia.learner.profile;

import com.pythontopia.learner.profile.service.PassiveProfileService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class ProfileDimensionProjectionProcessor {

  private final ProfileDimensionProjectionOutboxRepository repository;
  private final PassiveProfileService profileService;
  private final TransactionTemplate transaction;

  ProfileDimensionProjectionProcessor(
      ProfileDimensionProjectionOutboxRepository repository,
      PassiveProfileService profileService,
      PlatformTransactionManager transactionManager) {
    this.repository = repository;
    this.profileService = profileService;
    this.transaction = new TransactionTemplate(transactionManager);
    this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  boolean process(long outboxId) {
    Boolean applied =
        transaction.execute(
            status -> {
              var projection = repository.findPendingForUpdate(outboxId).orElse(null);
              if (projection == null
                  || repository.hasEarlierUnresolved(projection.userId(), outboxId)) {
                return false;
              }
              profileService.applyDimensionUpdate(
                  projection.userId(), projection.dimensionKey(), projection.delta());
              return repository.markApplied(outboxId);
            });
    return Boolean.TRUE.equals(applied);
  }
}
