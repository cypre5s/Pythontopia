package com.pythontopia.admin;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.common.web.CurrentUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/course-resources")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCourseResourceController {

  private final AdminCourseResourceService service;

  public AdminCourseResourceController(AdminCourseResourceService service) {
    this.service = service;
  }

  @GetMapping
  public AdminCourseResourcePageDto page(
      @CurrentUser CurrentUserPrincipal admin,
      @RequestParam(defaultValue = "0") @Min(0) @Max(100_000) int page,
      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
      @RequestParam(required = false) @Size(max = 100) String q,
      @RequestParam(defaultValue = "ALL") AdminCourseResourceType type) {
    return service.page(admin, page, size, q, type);
  }

  @PutMapping("/{type}/{resourceId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void setEnabled(
      @CurrentUser CurrentUserPrincipal admin,
      @PathVariable AdminCourseResourceType type,
      @PathVariable @Min(1) long resourceId,
      @jakarta.validation.Valid @RequestBody SetResourceEnabledRequest request) {
    service.setEnabled(admin, type, resourceId, request.enabled());
  }

  public record SetResourceEnabledRequest(@NotNull Boolean enabled) {}
}
