package com.pythontopia.world.controller;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.world.dto.PlayerStateDto;
import com.pythontopia.world.dto.PlayerStatePatch;
import com.pythontopia.world.dto.WorldSessionDto;
import com.pythontopia.world.service.WorldService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/world")
public class WorldController {

  private final WorldService service;
  private final StudentWriteThrottle studentWriteThrottle;

  public WorldController(WorldService service, StudentWriteThrottle studentWriteThrottle) {
    this.service = service;
    this.studentWriteThrottle = studentWriteThrottle;
  }

  @GetMapping("/session")
  WorldSessionDto session(@CurrentUser CurrentUserPrincipal user) {
    return service.getOrCreateSession(user);
  }

  @PatchMapping("/player")
  PlayerStateDto patchPlayer(
      @CurrentUser CurrentUserPrincipal user, @Valid @RequestBody PlayerStatePatch request) {
    if (!studentWriteThrottle.tryAcquirePlayerPositionUpdate(user.userId())) {
      throw new BusinessException(
          HttpStatus.TOO_MANY_REQUESTS, "PLAYER_POSITION_RATE_LIMITED", "每个账号每秒最多更新 10 次玩家位置");
    }
    return service.patchPlayer(user, request);
  }
}
