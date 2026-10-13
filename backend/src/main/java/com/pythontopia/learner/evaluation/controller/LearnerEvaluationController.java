package com.pythontopia.learner.evaluation.controller;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.common.web.AiBudgeted;
import com.pythontopia.learner.evaluation.dto.EvaluationDto;
import com.pythontopia.learner.evaluation.dto.EvaluationRequest;
import com.pythontopia.learner.evaluation.service.LearnerEvaluationService;
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
@RequestMapping("/api/learner/evaluations")
public class LearnerEvaluationController {

  private final LearnerEvaluationService service;

  public LearnerEvaluationController(LearnerEvaluationService service) {
    this.service = service;
  }

  @PostMapping
  @AiBudgeted
  public EvaluationDto compute(
      @CurrentUser CurrentUserPrincipal user,
      @RequestBody(required = false) @Valid EvaluationRequest request) {
    requireStudent(user);
    return service.compute(user.userId(), request);
  }

  @GetMapping
  public List<EvaluationDto> list(
      @CurrentUser CurrentUserPrincipal user,
      @RequestParam(value = "limit", required = false) Integer limit) {
    requireStudent(user);
    return service.listForUser(user.userId(), limit);
  }

  @GetMapping("/{evaluationId}")
  public EvaluationDto detail(
      @CurrentUser CurrentUserPrincipal user, @PathVariable Long evaluationId) {
    requireStudent(user);
    return service.findById(user.userId(), evaluationId);
  }

  @PostMapping("/{evaluationId}/accept-new-path")
  public EvaluationDto accept(
      @CurrentUser CurrentUserPrincipal user, @PathVariable Long evaluationId) {
    requireStudent(user);
    return service.acceptNewPath(user.userId(), evaluationId);
  }

  @PostMapping("/{evaluationId}/keep-old")
  public EvaluationDto keepOld(
      @CurrentUser CurrentUserPrincipal user, @PathVariable Long evaluationId) {
    requireStudent(user);
    return service.keepOld(user.userId(), evaluationId);
  }

  private void requireStudent(CurrentUserPrincipal user) {
    if (user.role() != Role.STUDENT) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "仅学生可访问复盘大厅评估接口");
    }
  }
}
