package com.pythontopia.tutor;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.tutor.dto.TutorWorkflowRunRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 导学工作流入口（训练场 6 阶段卡片式导学，移植 Alethicode tutor workflow）。
 *
 * <p>{@code POST /runs} 以 SSE 执行一次工作流事件（审题 / 思路 / 骨架 / 编码 / 错因 / 复盘 /
 * 迁移 / 阶段内对话）；{@code GET /sessions/active} 取该题活跃会话快照 + 卡片时间线（前端
 * 刷新恢复）。仅学生可用（导学是学生侧学习闭环，教师视角走班级 copilot）。
 */
@RestController
@RequestMapping("/api/tutor/workflow")
public class TutorWorkflowController {

  private final TutorWorkflowService service;

  public TutorWorkflowController(TutorWorkflowService service) {
    this.service = service;
  }

  @PostMapping(value = "/runs", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public org.springframework.web.servlet.mvc.method.annotation.SseEmitter run(
      @CurrentUser CurrentUserPrincipal user,
      @Valid @RequestBody TutorWorkflowRunRequest request) {
    requireStudent(user);
    return service.run(user.userId(), request);
  }

  @GetMapping("/sessions/active")
  public Map<String, Object> activeSession(
      @CurrentUser CurrentUserPrincipal user, @RequestParam Long problemId) {
    requireStudent(user);
    return service.activeSession(user.userId(), problemId);
  }

  private void requireStudent(CurrentUserPrincipal user) {
    if (user.role() != Role.STUDENT) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅学生可使用导学工作流");
    }
  }
}
