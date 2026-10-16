package com.pythontopia.rag;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.rag.dto.RagQueryRequestDto;
import com.pythontopia.rag.dto.RagQueryResponseDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 课件证据检索的对外端点：仅教师 / 管理员可直接查询课件证据（{@code POST /api/rag/query}）。 */
@RestController
@RequestMapping("/api/rag")
public class RagController {

  private final RagFacade facade;
  private final StudentWriteThrottle studentWriteThrottle;

  public RagController(RagFacade facade, StudentWriteThrottle studentWriteThrottle) {
    this.facade = facade;
    this.studentWriteThrottle = studentWriteThrottle;
  }

  @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
  @PostMapping("/query")
  public RagQueryResponseDto query(
      @Valid @RequestBody RagQueryRequestDto request,
      @CurrentUser CurrentUserPrincipal principal) {
    if (principal.role() != Role.TEACHER && principal.role() != Role.ADMIN) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "仅教师/管理员可直接查询课件证据。");
    }
    if (!studentWriteThrottle.tryAcquireRetrievalRequest(principal.userId())) {
      throw new BusinessException(
          HttpStatus.TOO_MANY_REQUESTS,
          "RETRIEVAL_RATE_LIMITED",
          "每个账号每分钟最多发起 30 次课件或个人语料检索");
    }
    return facade.search(request);
  }
}
