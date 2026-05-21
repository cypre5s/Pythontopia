package com.pythontopia.bugmine.controller;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.bugmine.dto.BugMineCardDto;
import com.pythontopia.bugmine.dto.BugMineOpenRequest;
import com.pythontopia.bugmine.dto.BugMineRetryRequest;
import com.pythontopia.bugmine.dto.BugMineSessionDto;
import com.pythontopia.bugmine.dto.BugMineSessionStatus;
import com.pythontopia.bugmine.service.BugMineService;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.submission.dto.SubmissionDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bug-mine/sessions")
public class BugMineController {

  private final BugMineService service;

  public BugMineController(BugMineService service) {
    this.service = service;
  }

  @PostMapping
  public BugMineSessionDto open(
      @CurrentUser CurrentUserPrincipal user,
      @Valid @RequestBody BugMineOpenRequest request) {
    requireStudent(user);
    return service.open(user.userId(), request.submissionId());
  }

  @GetMapping("/{id}")
  public BugMineSessionDto find(@CurrentUser CurrentUserPrincipal user, @PathVariable Long id) {
    requireStudent(user);
    return service.find(user.userId(), id);
  }

  @PostMapping("/{id}/cards/{cardId}/reveal")
  public BugMineCardDto reveal(
      @CurrentUser CurrentUserPrincipal user,
      @PathVariable Long id,
      @PathVariable Long cardId) {
    requireStudent(user);
    return service.reveal(user.userId(), id, cardId);
  }

  @PostMapping("/{id}/retry-submit")
  public SubmissionDto retrySubmit(
      @CurrentUser CurrentUserPrincipal user,
      @PathVariable Long id,
      @Valid @RequestBody BugMineRetryRequest request) {
    requireStudent(user);
    return service.retrySubmit(user.userId(), id, request.code());
  }

  @PostMapping("/{id}/close")
  public BugMineSessionDto close(
      @CurrentUser CurrentUserPrincipal user,
      @PathVariable Long id,
      @RequestParam BugMineSessionStatus status) {
    requireStudent(user);
    return service.close(user.userId(), id, status);
  }

  private void requireStudent(CurrentUserPrincipal user) {
    if (user.role() != Role.STUDENT) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "仅学生可访问 Bug 矿洞");
    }
  }
}
