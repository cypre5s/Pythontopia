package com.pythontopia.learner.twin;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.learner.twin.dto.LearnerTwinStateDto;
import com.pythontopia.learner.twin.dto.WeeklyReportDto;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数字孪生只读端点：返回「本周你的 AI 团队」时间线聚合数据。
 * 仅学生可用，只读本人。
 */
@RestController
@RequestMapping("/api/learner/twin")
public class TwinWeeklyController {

  private final WeeklyReportService service;
  private final LearnerTwinService learnerTwinService;

  public TwinWeeklyController(WeeklyReportService service, LearnerTwinService learnerTwinService) {
    this.service = service;
    this.learnerTwinService = learnerTwinService;
  }

  @GetMapping("/state")
  public LearnerTwinStateDto state(@CurrentUser CurrentUserPrincipal user) {
    requireStudent(user);
    return learnerTwinService.state(user.userId());
  }

  @GetMapping("/weekly")
  public WeeklyReportDto weekly(@CurrentUser CurrentUserPrincipal user) {
    requireStudent(user);
    return service.build(user.userId());
  }

  private void requireStudent(CurrentUserPrincipal user) {
    if (user.role() != Role.STUDENT) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "仅学生可查看数字孪生周报");
    }
  }
}
