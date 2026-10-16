package com.pythontopia.mainline.practice;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.mainline.practice.dto.PracticeAnswerRequest;
import com.pythontopia.mainline.practice.dto.PracticeAnswerResultDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 主线学生端「练-客观题」作答（JWT + STUDENT）。角色校验由类级 {@code @PreAuthorize} 强制， {@link CurrentUserPrincipal} 提供
 * userId；判分 + 进度打点在服务端（防泄题，见 {@link MainlinePracticeService}）。
 */
@Validated
@RestController
@RequestMapping("/api/mainline")
@PreAuthorize("hasRole('STUDENT')")
public class MainlinePracticeController {

  private final MainlinePracticeService service;
  private final StudentWriteThrottle studentWriteThrottle;

  public MainlinePracticeController(
      MainlinePracticeService service, StudentWriteThrottle studentWriteThrottle) {
    this.service = service;
    this.studentWriteThrottle = studentWriteThrottle;
  }

  /** 提交某小节某客观题作答，即时判分并回传正确答案 + 讲解供自检。 */
  @PostMapping("/stages/{stageId}/practice/{questionId}/answer")
  public PracticeAnswerResultDto answer(
      @PathVariable @Positive long stageId,
      @PathVariable @Positive long questionId,
      @Valid @RequestBody PracticeAnswerRequest request,
      @CurrentUser CurrentUserPrincipal user) {
    long userId = requireUserId(user);
    if (!studentWriteThrottle.tryAcquire(userId, 1)) {
      throw new BusinessException(
          HttpStatus.TOO_MANY_REQUESTS,
          "STUDENT_WRITE_RATE_LIMITED",
          "每个账号每分钟最多写入 100 条记录");
    }
    return service.grade(userId, stageId, questionId, request.answers());
  }

  private long requireUserId(CurrentUserPrincipal user) {
    if (user == null || user.userId() == null) {
      throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "需要登录");
    }
    return user.userId();
  }
}
