package com.pythontopia.world.skill;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.world.skill.dto.LearnerSkillDto;
import com.pythontopia.world.skill.dto.SkillsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/world/skills")
public class SkillController {

  private final SkillService service;

  public SkillController(SkillService service) {
    this.service = service;
  }

  @GetMapping
  public SkillsResponse list(@CurrentUser CurrentUserPrincipal user) {
    return service.list(user.userId());
  }

  @PostMapping("/{skillKey}/unlock")
  public LearnerSkillDto unlock(
      @CurrentUser CurrentUserPrincipal user, @PathVariable String skillKey) {
    return service.unlock(user.userId(), skillKey);
  }
}
