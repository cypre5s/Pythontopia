package com.pythontopia.learner.quest.controller;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.learner.quest.dto.QuestCompleteRequest;
import com.pythontopia.learner.quest.dto.QuestDto;
import com.pythontopia.learner.quest.dto.QuestSkipRequest;
import com.pythontopia.learner.quest.dto.QuestSource;
import com.pythontopia.learner.quest.dto.QuestStatus;
import com.pythontopia.learner.quest.service.QuestService;
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
@RequestMapping("/api/quests")
public class QuestController {

  private final StudentWriteThrottle studentWriteThrottle;
  private final QuestService service;

  public QuestController(QuestService service, StudentWriteThrottle studentWriteThrottle) {
    this.service = service;
    this.studentWriteThrottle = studentWriteThrottle;
  }

  @GetMapping("/today")
  public List<QuestDto> today(@CurrentUser CurrentUserPrincipal user) {
    requireStudent(user);
    return service.today(user.userId());
  }

  @GetMapping
  public List<QuestDto> list(
      @CurrentUser CurrentUserPrincipal user,
      @RequestParam(required = false) QuestStatus status,
      @RequestParam(required = false) QuestSource source,
      @RequestParam(defaultValue = "30") int limit,
      @RequestParam(defaultValue = "0") int offset) {
    requireStudent(user);
    return service.list(user.userId(), status, source, limit, offset);
  }

  @PostMapping("/{id}/start")
  public QuestDto start(@CurrentUser CurrentUserPrincipal user, @PathVariable Long id) {
    requireStudent(user);
    requireWriteCapacity(user.userId());
    return service.start(user.userId(), id);
  }

  @PostMapping("/{id}/complete")
  public QuestDto complete(
      @CurrentUser CurrentUserPrincipal user,
      @PathVariable Long id,
      @Valid @RequestBody(required = false) QuestCompleteRequest request) {
    requireStudent(user);
    requireWriteCapacity(user.userId());
    return service.complete(user.userId(), id, request);
  }

  @PostMapping("/{id}/skip")
  public QuestDto skip(
      @CurrentUser CurrentUserPrincipal user,
      @PathVariable Long id,
      @Valid @RequestBody(required = false) QuestSkipRequest request) {
    requireStudent(user);
    requireWriteCapacity(user.userId());
    return service.skip(user.userId(), id, request);
  }

  private void requireWriteCapacity(long userId) {
    if (!studentWriteThrottle.tryAcquire(userId, 1)) {
      throw new BusinessException(
          HttpStatus.TOO_MANY_REQUESTS,
          "STUDENT_WRITE_RATE_LIMITED",
          "每个账号每分钟最多写入 100 条记录");
    }
  }

  private void requireStudent(CurrentUserPrincipal user) {
    if (user.role() != Role.STUDENT) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "仅学生可访问公告板任务");
    }
  }
}
