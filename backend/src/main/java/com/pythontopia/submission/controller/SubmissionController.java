package com.pythontopia.submission.controller;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.submission.dto.SubmissionCreateRequest;
import com.pythontopia.submission.dto.SubmissionDetailDto;
import com.pythontopia.submission.dto.SubmissionDto;
import com.pythontopia.submission.dto.SubmissionStatus;
import com.pythontopia.submission.dto.SubmissionPerformanceDto;
import com.pythontopia.submission.service.SubmissionService;
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
@RequestMapping("/api/submissions")
public class SubmissionController {

  private final SubmissionService service;

  public SubmissionController(SubmissionService service) {
    this.service = service;
  }

  @PostMapping
  public SubmissionDto submit(
      @CurrentUser CurrentUserPrincipal user,
      @Valid @RequestBody SubmissionCreateRequest request) {
    requireStudent(user);
    return service.submit(user.userId(), request);
  }

  @GetMapping("/{id}")
  public SubmissionDetailDto findOne(
      @CurrentUser CurrentUserPrincipal user, @PathVariable Long id) {
    requireStudent(user);
    return service.findOne(user.userId(), id);
  }

  @GetMapping("/{id}/performance")
  public SubmissionPerformanceDto performance(
      @CurrentUser CurrentUserPrincipal user, @PathVariable Long id) {
    requireStudent(user);
    return service.performance(user.userId(), id);
  }

  @GetMapping
  public List<SubmissionDto> list(
      @CurrentUser CurrentUserPrincipal user,
      @RequestParam(required = false) Long problemId,
      @RequestParam(required = false) SubmissionStatus status,
      @RequestParam(defaultValue = "30") int limit,
      @RequestParam(defaultValue = "0") int offset) {
    requireStudent(user);
    return service.list(user.userId(), problemId, status, limit, offset);
  }

  private void requireStudent(CurrentUserPrincipal user) {
    if (user.role() != Role.STUDENT) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "仅学生可访问训练场提交接口");
    }
  }
}
