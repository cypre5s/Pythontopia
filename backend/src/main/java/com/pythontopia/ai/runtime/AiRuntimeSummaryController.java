package com.pythontopia.ai.runtime;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/runtime")
public class AiRuntimeSummaryController {

  private final AiRuntimeSummaryService service;

  public AiRuntimeSummaryController(AiRuntimeSummaryService service) {
    this.service = service;
  }

  @GetMapping("/summary")
  public AiRuntimeSummaryDto summary(@CurrentUser CurrentUserPrincipal user) {
    requireStudent(user);
    return service.summaryForUser(user.userId());
  }

  @GetMapping("/operations")
  public AiRuntimeOperationsDto operations(@CurrentUser CurrentUserPrincipal user) {
    requireRuntimeViewer(user);
    return user.role() == Role.STUDENT
        ? service.operationsForUser(user.userId())
        : service.operationsForPlatform();
  }

  private void requireStudent(CurrentUserPrincipal user) {
    if (user.role() != Role.STUDENT) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅学生可查看 AI Runtime 摘要");
    }
  }

  private void requireRuntimeViewer(CurrentUserPrincipal user) {
    if (user.role() != Role.STUDENT && user.role() != Role.TEACHER && user.role() != Role.ADMIN) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权查看 AI Runtime 运维状态");
    }
  }
}
