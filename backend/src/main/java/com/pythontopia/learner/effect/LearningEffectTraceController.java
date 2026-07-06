package com.pythontopia.learner.effect;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/learner/effect")
public class LearningEffectTraceController {

  private final LearningEffectTraceService service;

  public LearningEffectTraceController(LearningEffectTraceService service) {
    this.service = service;
  }

  @GetMapping("/summary")
  public LearningEffectSummaryDto summary(@CurrentUser CurrentUserPrincipal user) {
    if (user.role() != Role.STUDENT) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅学生可查看学习闭环摘要");
    }
    return service.summary(user.userId());
  }
}
