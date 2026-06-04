package com.pythontopia.learner.worldstate;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.learner.worldstate.dto.WorldStateDto;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 情绪化小镇②读接口：返回当前学生的小镇心情天气（由 emotion_persistence + 最近练习势头派生）。
 * 前端 {@code useMoodStore} 据此渲染 MoodOverlay；冷启动/无数据降级为晴天。
 */
@RestController
@RequestMapping("/api/learner/world-state")
public class WorldStateController {

  private final WorldStateService service;

  public WorldStateController(WorldStateService service) {
    this.service = service;
  }

  @GetMapping
  public WorldStateDto worldState(@CurrentUser CurrentUserPrincipal user) {
    requireStudent(user);
    return service.current(user.userId());
  }

  private void requireStudent(CurrentUserPrincipal user) {
    if (user.role() != Role.STUDENT) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅学生拥有小镇心情状态");
    }
  }
}
