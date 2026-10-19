package com.pythontopia.learner.profile;

import com.pythontopia.common.error.BusinessException;
import com.pythontopia.learner.profile.dto.ProfileDimensionProjectionRepairRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Restricted operational repair endpoint; response includes no learner or projection payload. */
@Validated
@RestController
@RequestMapping("/api/admin/profile-projection-outbox")
@PreAuthorize("hasRole('ADMIN')")
public class ProfileDimensionProjectionOutboxAdminController {

  private final ProfileDimensionProjectionOutboxService service;

  public ProfileDimensionProjectionOutboxAdminController(
      ProfileDimensionProjectionOutboxService service) {
    this.service = service;
  }

  @PutMapping("/{outboxId}")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public ReplayAccepted repairAndReplay(
      @PathVariable @Positive long outboxId,
      @Valid @RequestBody ProfileDimensionProjectionRepairRequest request) {
    if (!service.repairAndReplay(outboxId, request.dimensionKey(), request.toDimensionDelta())) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "PROFILE_PROJECTION_NOT_FAILED",
          "Profile projection is missing or is not failed");
    }
    return new ReplayAccepted(outboxId, "ACCEPTED");
  }

  public record ReplayAccepted(long outboxId, String status) {}
}
