package com.pythontopia.world;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.world.dto.GameBootstrapResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 进入小镇的聚合启动接口：一次性返回 world、player、profile、任务、掌握度、学习路径、技能、NPC 状态、
 * 当前存档槽与待处理日历事件。前端像素小镇在登录及每次章节重置后调用。
 */
@RestController
@RequestMapping("/api/game")
public class GameBootstrapController {

  private final GameBootstrapService service;
  private final StudentWriteThrottle studentWriteThrottle;

  public GameBootstrapController(
      GameBootstrapService service, StudentWriteThrottle studentWriteThrottle) {
    this.service = service;
    this.studentWriteThrottle = studentWriteThrottle;
  }

  @PostMapping("/bootstrap")
  public GameBootstrapResponse bootstrap(@CurrentUser CurrentUserPrincipal user) {
    requireStudentOrAdmin(user);
    if (!studentWriteThrottle.tryAcquireGameBootstrap(user.userId())) {
      throw new BusinessException(
          HttpStatus.TOO_MANY_REQUESTS,
          "GAME_BOOTSTRAP_RATE_LIMITED",
          "每个账号每分钟最多请求小镇数据 10 次");
    }
    return service.bootstrap(user.userId());
  }

  private void requireStudentOrAdmin(CurrentUserPrincipal user) {
    if (user.role() != Role.STUDENT && user.role() != Role.ADMIN) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "Only students or admins have world state");
    }
  }
}
