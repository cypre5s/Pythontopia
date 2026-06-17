package com.pythontopia.orchestrator;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.orchestrator.dto.OrchestratorTurnRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 学习总管（OrchestratorAgent）SSE 代理入口，镜像 {@code TutorController}：仅学生可用，校验后把请求
 * 交给 {@link OrchestratorService} 流式转发到 Python graph 服务。
 */
@RestController
@RequestMapping("/api/orchestrator")
public class OrchestratorController {

  private final OrchestratorService service;

  public OrchestratorController(OrchestratorService service) {
    this.service = service;
  }

  @PostMapping(value = "/turn/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter turnStream(
      @CurrentUser CurrentUserPrincipal user, @Valid @RequestBody OrchestratorTurnRequest request) {
    requireStudent(user);
    return service.turn(user.userId(), request);
  }

  /** 混合工具编排（Phase F）SSE：选择=确定性路由器，编排=有界工具循环；graph 侧 flag-gated。 */
  @PostMapping(value = "/tool-loop/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter toolLoopStream(
      @CurrentUser CurrentUserPrincipal user, @Valid @RequestBody OrchestratorTurnRequest request) {
    requireStudent(user);
    return service.toolLoop(user.userId(), request);
  }

  private void requireStudent(CurrentUserPrincipal user) {
    if (user.role() != Role.STUDENT) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅学生可使用学习总管");
    }
  }
}
