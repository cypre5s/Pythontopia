package com.pythontopia.tutorbench;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tutorbench")
public class TutorBenchController {

  private final TutorBenchService service;

  public TutorBenchController(TutorBenchService service) {
    this.service = service;
  }

  @PostMapping("/runs")
  public TutorBenchReportDto run(@CurrentUser CurrentUserPrincipal user) {
    if (user.role() != Role.STUDENT) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅学生可运行个人 TutorBench");
    }
    return service.run(user.userId());
  }
}
