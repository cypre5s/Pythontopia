package com.pythontopia.learner.path.controller;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.common.web.AiBudgeted;
import com.pythontopia.learner.path.dto.LearningPathDto;
import com.pythontopia.learner.path.dto.PlanPathResponse;
import com.pythontopia.learner.path.service.LearningPathService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/learner/path")
public class LearningPathController {

  private final LearningPathService service;

  public LearningPathController(LearningPathService service) {
    this.service = service;
  }

  @PostMapping("/plan")
  @AiBudgeted
  public PlanPathResponse plan(@CurrentUser CurrentUserPrincipal user) {
    requireStudent(user);
    return service.plan(user.userId());
  }

  @GetMapping("/active")
  public LearningPathDto active(@CurrentUser CurrentUserPrincipal user) {
    requireStudent(user);
    return service
        .findActive(user.userId())
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND, "PATH_NOT_FOUND", "尚未生成学习路径"));
  }

  @GetMapping("/archived")
  public List<LearningPathDto> archived(@CurrentUser CurrentUserPrincipal user) {
    requireStudent(user);
    return service.listArchived(user.userId());
  }

  @GetMapping("/{id}")
  public LearningPathDto byId(
      @CurrentUser CurrentUserPrincipal user, @PathVariable Long id) {
    requireStudent(user);
    return service.findById(user.userId(), id);
  }

  private void requireStudent(CurrentUserPrincipal user) {
    if (user.role() != Role.STUDENT) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "仅学生可生成与查看个人学习路径");
    }
  }
}
