package com.pythontopia.world.curriculum;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.world.curriculum.dto.CurriculumBootstrapResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/learner/curriculum")
public class CurriculumController {

  private final CurriculumMapperService service;

  public CurriculumController(CurriculumMapperService service) {
    this.service = service;
  }

  @PostMapping("/bootstrap")
  public CurriculumBootstrapResult bootstrap(@CurrentUser CurrentUserPrincipal principal) {
    return service.bootstrap(principal.userId());
  }
}
