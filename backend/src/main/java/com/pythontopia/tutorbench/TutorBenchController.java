package com.pythontopia.tutorbench;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.common.web.StudentWriteThrottle;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tutorbench")
public class TutorBenchController {

  private final TutorBenchService service;
  private final StudentWriteThrottle studentWriteThrottle;

  public TutorBenchController(
      TutorBenchService service, StudentWriteThrottle studentWriteThrottle) {
    this.service = service;
    this.studentWriteThrottle = studentWriteThrottle;
  }

  @PostMapping("/runs")
  public TutorBenchReportDto run(@CurrentUser CurrentUserPrincipal user) {
    if (user.role() != Role.STUDENT) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅学生可运行个人 TutorBench");
    }
    if (!studentWriteThrottle.tryAcquireTutorBenchRun(user.userId())) {
      throw new BusinessException(
          HttpStatus.TOO_MANY_REQUESTS,
          "TUTOR_BENCH_RATE_LIMITED",
          "每个账号每分钟最多运行 10 次学情审查");
    }
    return service.run(user.userId());
  }
}
