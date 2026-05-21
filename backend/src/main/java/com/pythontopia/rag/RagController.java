package com.pythontopia.rag;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.rag.dto.RagQueryRequestDto;
import com.pythontopia.rag.dto.RagQueryResponseDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rag")
public class RagController {

  private final RagFacade facade;

  public RagController(RagFacade facade) {
    this.facade = facade;
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
    return facade.searchByKcCodes(request.query(), request.kcCodes(), request.topK());
  }
}
