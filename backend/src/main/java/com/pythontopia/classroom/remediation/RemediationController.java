package com.pythontopia.classroom.remediation;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.web.AiBudgeted;
import com.pythontopia.common.web.CurrentUser;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 班级薄弱点一键补救入口（P6.1）。教师专用：{@code @PreAuthorize} 做角色门，{@link RemediationService}
 * 内再用真实当前教师过 {@code ClassroomGuard.requireOwner} 做班级归属校验（双层防御）。
 */
@RestController
@RequestMapping("/api/teacher/classrooms/{classroomId}/remediation")
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
public class RemediationController {

  private final RemediationService service;

  public RemediationController(RemediationService service) {
    this.service = service;
  }

  @PostMapping
  @AiBudgeted(roles = {Role.TEACHER, Role.ADMIN})
  public RemediationResultDto trigger(
      @CurrentUser CurrentUserPrincipal user, @PathVariable @Positive Long classroomId) {
    return service.trigger(user, classroomId);
  }
}
