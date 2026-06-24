package com.pythontopia.learner.evidence;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.common.web.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LearningEvidenceGraphController {

  private final LearningEvidenceGraphService service;

  public LearningEvidenceGraphController(LearningEvidenceGraphService service) {
    this.service = service;
  }

  @GetMapping("/api/learner/evidence-graph")
  public LearningEvidenceGraphDto graph(
      @CurrentUser CurrentUserPrincipal user,
      @RequestParam(required = false) String focusKc,
      @RequestParam(defaultValue = "80") int limit) {
    return service.buildForUser(user.userId(), focusKc, limit);
  }
}
