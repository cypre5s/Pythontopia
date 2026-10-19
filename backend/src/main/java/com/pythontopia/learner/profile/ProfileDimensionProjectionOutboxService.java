package com.pythontopia.learner.profile;

import com.pythontopia.learner.profile.dto.DimensionDelta;
import com.pythontopia.learner.profile.dto.ProfileDimensionProjectionOutboxDto;
import com.pythontopia.learner.profile.dto.ProfileDimensionProjectionOutboxPageDto;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import java.util.Optional;
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

  /**
   * Replaces a quarantined projection with an operator-corrected payload and schedules it again.
   * The learner and idempotency key stay bound to the original row.
   */
  @Transactional
  public boolean repairAndReplay(
      long outboxId, long adminUserId, String dimensionKey, DimensionDelta delta) {
    validateRepair(dimensionKey, delta);
    if (!repository.repairFailed(outboxId, adminUserId, dimensionKey, delta)) {
      return false;
    }
    eventPublisher.publishEvent(new ProfileDimensionProjectionQueued(outboxId));
    return true;
  }

  @Transactional(readOnly = true)
  public ProfileDimensionProjectionOutboxPageDto pageFailed(int page, int size) {
    long total = repository.countFailed();
    int totalPages = total == 0 ? 0 : Math.toIntExact((total + size - 1) / size);
    return new ProfileDimensionProjectionOutboxPageDto(
        repository.findFailed(page, size), page, size, total, totalPages);
  }

  @Transactional(readOnly = true)
  public Optional<ProfileDimensionProjectionOutboxDto> findForAdmin(long outboxId) {
    return repository.findForAdmin(outboxId);
  }

  private static void validateRepair(String dimensionKey, DimensionDelta delta) {
    if (dimensionKey == null || !PassiveProfileService.DIMENSION_KEYS.contains(dimensionKey)) {
      throw new IllegalArgumentException("unsupported profile dimension");
    }
    if (delta == null
        || (delta.scoreDelta() != null && !Double.isFinite(delta.scoreDelta()))
        || (delta.evidenceIncrement() != null && delta.evidenceIncrement() < 0)) {
      throw new IllegalArgumentException("invalid profile projection delta");
    }
  }
}
