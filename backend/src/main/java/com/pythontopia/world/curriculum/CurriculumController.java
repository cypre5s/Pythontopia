package com.pythontopia.world.curriculum;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.world.curriculum.dto.CurriculumBootstrapResult;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/learner/curriculum")
public class CurriculumController {

  private final CurriculumMapperService service;
  private final StudentWriteThrottle studentWriteThrottle;

  public CurriculumController(
      CurriculumMapperService service, StudentWriteThrottle studentWriteThrottle) {
    this.service = service;
    this.studentWriteThrottle = studentWriteThrottle;
  }

  @PostMapping("/bootstrap")
  public CurriculumBootstrapResult bootstrap(@CurrentUser CurrentUserPrincipal principal) {
    if (principal == null || principal.role() != Role.STUDENT) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅学生可初始化学习画像");
    }
    if (!studentWriteThrottle.tryAcquire(principal.userId(), 1)) {
      throw new BusinessException(
          HttpStatus.TOO_MANY_REQUESTS,
          "STUDENT_WRITE_RATE_LIMITED",
          "每个账号每分钟最多写入 100 条学习记录");
    }
    return service.bootstrap(principal.userId());
  }
}
