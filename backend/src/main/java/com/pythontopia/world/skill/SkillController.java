package com.pythontopia.world.skill;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.world.skill.dto.LearnerSkillDto;
import com.pythontopia.world.skill.dto.SkillsResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/world/skills")
public class SkillController {

  private final SkillService service;
  private final StudentWriteThrottle studentWriteThrottle;

  public SkillController(SkillService service, StudentWriteThrottle studentWriteThrottle) {
    this.service = service;
    this.studentWriteThrottle = studentWriteThrottle;
  }

  @GetMapping
  public SkillsResponse list(@CurrentUser CurrentUserPrincipal user) {
    return service.list(user.userId());
  }

  @PostMapping("/{skillKey}/unlock")
  public LearnerSkillDto unlock(
      @CurrentUser CurrentUserPrincipal user, @PathVariable String skillKey) {
    if (!studentWriteThrottle.tryAcquire(user.userId(), 1)) {
      throw new BusinessException(
          HttpStatus.TOO_MANY_REQUESTS,
          "STUDENT_WRITE_RATE_LIMITED",
          "每个账号每分钟最多写入 100 条记录");
    }
    return service.unlock(user.userId(), skillKey);
  }
}
