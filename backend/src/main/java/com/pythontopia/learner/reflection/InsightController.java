package com.pythontopia.learner.reflection;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.learner.reflection.dto.LearnerInsightDto;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学情洞察只读端点：倒序返回当前学生的反思洞察（含可溯源 evidenceEventIds）。仅学生可用、只读本人。
 */
@RestController
@RequestMapping("/api/learner/insights")
public class InsightController {

  private static final int MAX_LIMIT = 50;

  private final LearnerInsightRepository repository;

  public InsightController(LearnerInsightRepository repository) {
    this.repository = repository;
  }

  @GetMapping
  public List<LearnerInsightDto> insights(
      @CurrentUser CurrentUserPrincipal user,
      @RequestParam(defaultValue = "10") int limit) {
    requireStudent(user);
    int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
    return repository.findByUser(user.userId(), safeLimit);
  }

  private void requireStudent(CurrentUserPrincipal user) {
    if (user.role() != Role.STUDENT) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅学生可查看学情洞察");
    }
  }
}
