package com.pythontopia.learner.evidence;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.common.web.StudentWriteThrottle;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EvidenceMemoryController {

  private final EvidenceMemoryService service;
  private final StudentWriteThrottle studentWriteThrottle;

  public EvidenceMemoryController(
      EvidenceMemoryService service, StudentWriteThrottle studentWriteThrottle) {
    this.service = service;
    this.studentWriteThrottle = studentWriteThrottle;
  }

  @PostMapping("/api/learner/evidence-memory/snapshots")
  public EvidenceMemorySnapshotDto snapshot(
      @CurrentUser CurrentUserPrincipal user,
      @RequestParam(required = false) String focusKc,
      @RequestParam(defaultValue = "160") int limit) {
    if (!studentWriteThrottle.tryAcquire(user.userId(), 1)) {
      throw new BusinessException(
          HttpStatus.TOO_MANY_REQUESTS,
          "STUDENT_WRITE_RATE_LIMITED",
          "每个账号每分钟最多写入 100 条记录");
    }
    return service.createSnapshot(user.userId(), focusKc, limit, true);
  }
}
