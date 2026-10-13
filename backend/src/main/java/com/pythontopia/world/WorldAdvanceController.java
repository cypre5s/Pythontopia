package com.pythontopia.world;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.world.dto.WorldAdvanceResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/world")
public class WorldAdvanceController {

  private final WorldAdvanceService service;

  public WorldAdvanceController(WorldAdvanceService service) {
    this.service = service;
  }

  /**
   * 推进世界一个游戏内日：day_index +1，轮转 weekday/season/weather，应用掌握度遗忘曲线，
   * 并为新一天排入日历事件。
   */
  @PostMapping("/sleep")
  public WorldAdvanceResponse sleep(@CurrentUser CurrentUserPrincipal user) {
    return service.sleep(user.userId());
  }
}
