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
   * Advance the world by one in-game day: bump day_index, rotate weekday/season/weather, decay
   * mastery, bump emotion_persistence, and queue calendar events for the new day.
   */
  @PostMapping("/sleep")
  public WorldAdvanceResponse sleep(@CurrentUser CurrentUserPrincipal user) {
    return service.sleep(user.userId());
  }
}
