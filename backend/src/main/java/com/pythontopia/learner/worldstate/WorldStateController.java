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
 * Compatibility read interface: returns neutral town ambience and the objective recent practice streak.
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
