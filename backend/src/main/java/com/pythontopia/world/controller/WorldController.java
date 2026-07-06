package com.pythontopia.world.controller;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.world.dto.PlayerStateDto;
import com.pythontopia.world.dto.PlayerStatePatch;
import com.pythontopia.world.dto.WorldSessionDto;
import com.pythontopia.world.service.WorldService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/world")
public class WorldController {

  private final WorldService service;

  public WorldController(WorldService service) {
    this.service = service;
  }

  @GetMapping("/session")
  WorldSessionDto session(@CurrentUser CurrentUserPrincipal user) {
    return service.getOrCreateSession(user);
  }

  @PatchMapping("/player")
  PlayerStateDto patchPlayer(
      @CurrentUser CurrentUserPrincipal user, @Valid @RequestBody PlayerStatePatch request) {
    return service.patchPlayer(user, request);
  }
}
