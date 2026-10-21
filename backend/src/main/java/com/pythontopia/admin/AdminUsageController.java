package com.pythontopia.admin;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.common.web.CurrentUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/usage")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUsageController {

  private final AdminUsageService service;

  public AdminUsageController(AdminUsageService service) {
    this.service = service;
  }

  @GetMapping
  public AdminUsageDto list(
      @CurrentUser CurrentUserPrincipal admin,
      @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days,
      @RequestParam(required = false) @Size(max = 64) String username) {
    return service.list(admin, days, username);
  }
}
