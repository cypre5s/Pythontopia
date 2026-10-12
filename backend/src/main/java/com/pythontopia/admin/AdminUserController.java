package com.pythontopia.admin;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.web.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

  private final AdminUserService service;

  public AdminUserController(AdminUserService service) {
    this.service = service;
  }

  @GetMapping
  public AdminUserPageDto page(
      @CurrentUser CurrentUserPrincipal admin,
      @RequestParam(defaultValue = "0") @Min(0) @Max(100_000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @RequestParam(required = false) @Size(max = 100) String q,
      @RequestParam(required = false) AdminUserStatus status,
      @RequestParam(required = false) Role role) {
    return service.page(admin, page, size, q, status, role);
  }

  @PatchMapping("/{userId}")
  public AdminUserDto setStatus(
      @CurrentUser CurrentUserPrincipal admin,
      @PathVariable @Positive long userId,
      @Valid @RequestBody AdminUserStatusRequest request) {
    return service.setStatus(admin, userId, request.status());
  }
}
