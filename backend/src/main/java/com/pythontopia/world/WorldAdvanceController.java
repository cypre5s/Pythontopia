package com.pythontopia.world;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.world.dto.WorldAdvanceResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/world")
public class WorldAdvanceController {

  private final WorldAdvanceService service;
  private final StudentWriteThrottle studentWriteThrottle;

  public WorldAdvanceController(
      WorldAdvanceService service, StudentWriteThrottle studentWriteThrottle) {
    this.service = service;
    this.studentWriteThrottle = studentWriteThrottle;
  }

  /**
   * 推进世界一个游戏内日：day_index +1，轮转 weekday/season/weather，应用掌握度遗忘曲线，
   * 并为新一天排入日历事件。
   */
  @PostMapping("/sleep")
  public WorldAdvanceResponse sleep(@CurrentUser CurrentUserPrincipal user) {
    if (!studentWriteThrottle.tryAcquire(user.userId(), 1)) {
      throw new BusinessException(
          HttpStatus.TOO_MANY_REQUESTS,
          "STUDENT_WRITE_RATE_LIMITED",
          "每个账号每分钟最多写入 100 条记录");
    }
    return service.sleep(user.userId());
  }
}
