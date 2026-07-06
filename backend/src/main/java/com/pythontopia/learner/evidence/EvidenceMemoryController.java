package com.pythontopia.learner.evidence;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.common.web.CurrentUser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EvidenceMemoryController {

  private final EvidenceMemoryService service;

  public EvidenceMemoryController(EvidenceMemoryService service) {
    this.service = service;
  }

  @PostMapping("/api/learner/evidence-memory/snapshots")
  public EvidenceMemorySnapshotDto snapshot(
      @CurrentUser CurrentUserPrincipal user,
      @RequestParam(required = false) String focusKc,
      @RequestParam(defaultValue = "160") int limit) {
    return service.createSnapshot(user.userId(), focusKc, limit, true);
  }
}
