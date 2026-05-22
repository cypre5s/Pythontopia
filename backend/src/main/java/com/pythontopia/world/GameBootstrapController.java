package com.pythontopia.world;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.world.dto.GameBootstrapResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Single-shot hydration endpoint: returns world + player + profile + crops + quests + mastery +
 * path + inventory + skills + currency + npc state + active save slot + pending events. The
 * front-end pixel town pulls this on login and after every chapter reset.
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
