package com.pythontopia.learner.profile;

import com.pythontopia.common.error.BusinessException;
import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.learner.profile.dto.ProfileDimensionProjectionOutboxDto;
import com.pythontopia.learner.profile.dto.ProfileDimensionProjectionOutboxPageDto;
import com.pythontopia.learner.profile.dto.ProfileDimensionProjectionRepairRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
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

  @GetMapping("/failed")
  public ProfileDimensionProjectionOutboxPageDto listFailed(
      @RequestParam(defaultValue = "0") @Min(0) @Max(100_000) int page,
      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
    return service.pageFailed(page, size);
  }

  @GetMapping("/{outboxId}")
  public ProfileDimensionProjectionOutboxDto get(
      @PathVariable @Positive long outboxId) {
    return service
        .findForAdmin(outboxId)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND,
                    "PROFILE_PROJECTION_OUTBOX_NOT_FOUND",
                    "Profile projection outbox was not found"));
  }

  @PutMapping("/{outboxId}")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public ReplayAccepted repairAndReplay(
      @CurrentUser CurrentUserPrincipal admin,
      @PathVariable @Positive long outboxId,
      @Valid @RequestBody ProfileDimensionProjectionRepairRequest request) {
    if (!service.repairAndReplay(
        outboxId, admin.userId(), request.dimensionKey(), request.toDimensionDelta())) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "PROFILE_PROJECTION_NOT_FAILED",
          "Profile projection is missing or is not failed");
    }
    return new ReplayAccepted(outboxId, "ACCEPTED");
  }

  public record ReplayAccepted(long outboxId, String status) {}
}
