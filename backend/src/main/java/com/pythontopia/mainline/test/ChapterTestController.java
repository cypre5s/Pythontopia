package com.pythontopia.mainline.test;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.AiBudgeted;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.mainline.dto.ChapterTestPaperDto;
import com.pythontopia.mainline.dto.ChapterTestResultDto;
import com.pythontopia.mainline.dto.ChapterTestSubmitRequest;
import com.pythontopia.mainline.dto.TestPrepareResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 章末大测试学生端（r15 计划 §9）：prepare（触发预生成）/ start（去答案发卷）/ submit（服务端判分）/ latest。 考试期间零
 * LLM；防作弊（去答案、时限硬校验、粘贴/失焦计数）见 ChapterTestService / §4.3。
 */
@RestController
@RequestMapping("/api/mainline/chapters/{chapter}/test")
@PreAuthorize("hasRole('STUDENT')")
public class ChapterTestController {

  private final ChapterTestService service;
  private final StudentWriteThrottle studentWriteThrottle;

  public ChapterTestController(
      ChapterTestService service, StudentWriteThrottle studentWriteThrottle) {
    this.service = service;
    this.studentWriteThrottle = studentWriteThrottle;
  }

  @PostMapping("/prepare")
  @AiBudgeted(roles = Role.STUDENT)
  public TestPrepareResponse prepare(
      @PathVariable @Min(1) @Max(7) int chapter, @CurrentUser CurrentUserPrincipal user) {
    return service.prepare(requireUserId(user), chapter);
  }

  @PostMapping("/start")
  public ChapterTestPaperDto start(
      @PathVariable @Min(1) @Max(7) int chapter, @CurrentUser CurrentUserPrincipal user) {
    long userId = requireUserId(user);
    requireWriteCapacity(userId);
    return service.start(userId, chapter);
  }

  @PostMapping("/{testId}/submit")
  public ChapterTestResultDto submit(
      @PathVariable @Min(1) @Max(7) int chapter,
      @PathVariable @Positive long testId,
      @Valid @RequestBody ChapterTestSubmitRequest request,
      @CurrentUser CurrentUserPrincipal user) {
    long userId = requireUserId(user);
    requireWriteCapacity(userId);
    return service.submit(userId, chapter, testId, request);
  }

  @GetMapping("/latest")
  public ChapterTestResultDto latest(
      @PathVariable @Min(1) @Max(7) int chapter, @CurrentUser CurrentUserPrincipal user) {
    return service.latest(requireUserId(user), chapter);
  }

  private long requireUserId(CurrentUserPrincipal user) {
    if (user == null || user.userId() == null) {
      throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "需要登录");
    }
    return user.userId();
  }

  private void requireWriteCapacity(long userId) {
    if (!studentWriteThrottle.tryAcquire(userId, 1)) {
      throw new BusinessException(
          HttpStatus.TOO_MANY_REQUESTS, "STUDENT_WRITE_RATE_LIMITED", "每个账号每分钟最多写入 100 条记录");
    }
  }
}
