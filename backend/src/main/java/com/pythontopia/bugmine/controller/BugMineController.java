package com.pythontopia.bugmine.controller;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.bugmine.dto.BugMineCardDto;
import com.pythontopia.bugmine.dto.BugMineEchoCandidateDto;
import com.pythontopia.bugmine.dto.BugMineEchoOpenRequest;
import com.pythontopia.bugmine.dto.BugMineErrorBookItemDto;
import com.pythontopia.bugmine.dto.BugMineOpenRequest;
import com.pythontopia.bugmine.dto.BugMineRetryRequest;
import com.pythontopia.bugmine.dto.BugMineSessionDto;
import com.pythontopia.bugmine.dto.BugMineSessionStatus;
import com.pythontopia.bugmine.service.BugMineService;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.submission.dto.SubmissionDto;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bug-mine")
public class BugMineController {

  private final BugMineService service;

  public BugMineController(BugMineService service) {
    this.service = service;
  }

  @PostMapping("/sessions")
  public BugMineSessionDto open(
      @CurrentUser CurrentUserPrincipal user,
      @Valid @RequestBody BugMineOpenRequest request) {
    requireStudent(user);
    return service.open(user.userId(), request.submissionId());
  }

  @GetMapping("/sessions/{id}")
  public BugMineSessionDto find(@CurrentUser CurrentUserPrincipal user, @PathVariable Long id) {
    requireStudent(user);
    return service.find(user.userId(), id);
  }

  @GetMapping("/errorbook")
  public List<BugMineErrorBookItemDto> errorBook(@CurrentUser CurrentUserPrincipal user) {
    requireStudent(user);
    return service.errorBook(user.userId());
  }

  @GetMapping("/echoes")
  public List<BugMineEchoCandidateDto> echoes(@CurrentUser CurrentUserPrincipal user) {
    requireStudent(user);
    return service.echoCandidates(user.userId());
  }

  @PostMapping("/echoes")
  public BugMineSessionDto openEcho(
      @CurrentUser CurrentUserPrincipal user,
      @Valid @RequestBody BugMineEchoOpenRequest request) {
    requireStudent(user);
    return service.openEcho(user.userId(), request.kcCode());
  }

  @PostMapping("/sessions/{id}/cards/{cardId}/reveal")
  public BugMineCardDto reveal(
      @CurrentUser CurrentUserPrincipal user,
      @PathVariable Long id,
      @PathVariable Long cardId) {
    requireStudent(user);
    return service.reveal(user.userId(), id, cardId);
  }

  @PostMapping("/sessions/{id}/retry-submit")
  public SubmissionDto retrySubmit(
      @CurrentUser CurrentUserPrincipal user,
      @PathVariable Long id,
      @Valid @RequestBody BugMineRetryRequest request) {
    requireStudent(user);
    return service.retrySubmit(user.userId(), id, request.code());
  }

  @PostMapping("/sessions/{id}/variant-submit")
  public SubmissionDto variantSubmit(
      @CurrentUser CurrentUserPrincipal user,
      @PathVariable Long id,
      @Valid @RequestBody BugMineRetryRequest request) {
    requireStudent(user);
    return service.variantSubmit(user.userId(), id, request.code());
  }

  @PostMapping("/sessions/{id}/close")
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
