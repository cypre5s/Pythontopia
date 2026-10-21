package com.pythontopia.admin;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.common.web.CurrentUser;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminStudentDetailController {

  private final AdminStudentDetailService service;

  public AdminStudentDetailController(AdminStudentDetailService service) {
    this.service = service;
  }

  @GetMapping("/{userId}/detail")
  public AdminStudentDetailDto get(
      @CurrentUser CurrentUserPrincipal admin, @PathVariable @Positive long userId) {
    return service.get(admin, userId);
  }
}
