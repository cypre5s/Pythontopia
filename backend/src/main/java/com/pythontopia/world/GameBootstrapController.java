package com.pythontopia.world;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.world.dto.GameBootstrapResponse;
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

  public GameBootstrapController(GameBootstrapService service) {
    this.service = service;
  }

  @PostMapping("/bootstrap")
  public GameBootstrapResponse bootstrap(@CurrentUser CurrentUserPrincipal user) {
    return service.bootstrap(user.userId());
  }
}
