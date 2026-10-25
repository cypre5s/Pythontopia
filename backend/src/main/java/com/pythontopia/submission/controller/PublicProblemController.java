package com.pythontopia.submission.controller;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.submission.dto.PublicProblemDto;
import com.pythontopia.submission.repository.PublicProblemRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/problems")
public class PublicProblemController {

  private final PublicProblemRepository repository;

  public PublicProblemController(PublicProblemRepository repository) {
    this.repository = repository;
  }

  @GetMapping("/{id}/public")
  public PublicProblemDto findPublic(
      @CurrentUser CurrentUserPrincipal user, @PathVariable Long id) {
    if (user.role() != Role.STUDENT) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "仅学生可访问公开题目视图");
    }
    return repository
        .findAccessible(43L, id, user.userId())
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND, "PROBLEM_NOT_FOUND", "未找到该题目"));
  }
}
