package com.pythontopia.learner.mastery;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.learner.mastery.dto.MasterySnapshotDto;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/learner/mastery")
public class MasteryController {

  private final MasteryService service;

  public MasteryController(MasteryService service) {
    this.service = service;
  }

  @GetMapping
  public List<MasterySnapshotDto> snapshot(@CurrentUser CurrentUserPrincipal principal) {
    return service.listSnapshots(principal.userId());
  }
}
