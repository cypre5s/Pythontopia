package com.pythontopia.ai.runtime;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/runtime")
public class AiRuntimeSummaryController {

  private final AiRuntimeSummaryService service;

  public AiRuntimeSummaryController(AiRuntimeSummaryService service) {
    this.service = service;
  }

  @GetMapping("/summary")
  public AiRuntimeSummaryDto summary(@CurrentUser CurrentUserPrincipal user) {
    requireStudent(user);
    return service.summaryForUser(user.userId());
  }

  /**
   * 闭环运维状态。
   *
   * <p>学生只能看自己；教师/管理员<strong>必须</strong>显式指定 {@code userId}——面板下的三个数据源
   * 都只有 per-user 维度，不指定就只能拿查看者（教师）自己的学习证据充数，而教师没有学习事件，
   * 结果恒为 INSUFFICIENT_EVIDENCE 且与平台真实状态无关。宁可 fail fast 也不做这种静默降级。
   */
  @GetMapping("/operations")
  public AiRuntimeOperationsDto operations(
      @CurrentUser CurrentUserPrincipal user,
      @RequestParam(name = "userId", required = false) Long targetUserId) {
    requireRuntimeViewer(user);
    if (user.role() == Role.STUDENT) {
      // CurrentUserPrincipal.userId() 是 Long：必须 equals 比较，`!=` 会退化成引用比较。
      if (targetUserId != null && !targetUserId.equals(user.userId())) {
        throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "学生只能查看本人的运维状态");
      }
      return service.operationsForSubject(
          AiRuntimeOperationsDto.OperationsSubject.self(user.userId()));
    }
    if (targetUserId == null) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "AI_RUNTIME_SUBJECT_REQUIRED",
          "闭环运维状态按学习者维度统计，请指定要查看的学生 userId");
    }
    return service.operationsForSubject(
        AiRuntimeOperationsDto.OperationsSubject.student(targetUserId));
  }

  private void requireStudent(CurrentUserPrincipal user) {
    if (user.role() != Role.STUDENT) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅学生可查看 AI Runtime 摘要");
    }
  }

  private void requireRuntimeViewer(CurrentUserPrincipal user) {
    if (user.role() != Role.STUDENT && user.role() != Role.TEACHER && user.role() != Role.ADMIN) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权查看 AI Runtime 运维状态");
    }
  }
}
