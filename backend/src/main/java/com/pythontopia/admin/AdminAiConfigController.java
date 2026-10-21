package com.pythontopia.admin;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.common.web.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/ai-config")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAiConfigController {
  private final AdminAiConfigService service;

  public AdminAiConfigController(AdminAiConfigService service) { this.service = service; }

  @GetMapping
  public AdminAiConfigDto get(@CurrentUser CurrentUserPrincipal admin) { return service.get(admin); }

  @PostMapping("/test")
  public AdminAiConfigGraphClient.GraphTestResult test(
      @CurrentUser CurrentUserPrincipal admin, @Valid @RequestBody AdminAiConfigRequest request) {
    return service.test(admin, request.model(), request.apiKey());
  }

  @PutMapping
  public AdminAiConfigDto update(
      @CurrentUser CurrentUserPrincipal admin, @Valid @RequestBody AdminAiConfigRequest request) {
    return service.update(admin, request.model(), request.apiKey());
  }

  public record AdminAiConfigRequest(
      @Pattern(regexp = "deepseek-(flash|v4-pro)") String model,
      @Size(min = 8, max = 512) String apiKey) {}
}
