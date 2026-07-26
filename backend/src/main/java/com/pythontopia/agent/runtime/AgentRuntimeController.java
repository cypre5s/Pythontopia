package com.pythontopia.agent.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.loop.LoopRunService;
import com.pythontopia.loop.LoopRuntimeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent Runtime 控制面（均需 STUDENT）：
 *
 * <ul>
 *   <li>{@code POST /api/events}：领域事件入库（graph / 前端上报）；
 *   <li>{@code POST /api/agent-runs/{id}/loop-states}：闭环状态机快照；
 *   <li>{@code POST /api/agent-runs/{id}/policy-decisions}：策略判定审计。
 * </ul>
 *
 * <p><b>run 本身的创建与查询不在这里</b>：唯一入口是 {@link com.pythontopia.loop.LoopController} 的
 * {@code POST /api/loop/runs} 与 {@code GET /api/loop/runs/{id}}（同一 {@link LoopRunService}、同一
 * {@code loop_run} 表）。历史上这里还镜像了一份 {@code POST /api/agent-runs} / {@code GET
 * /api/agent-runs/{id}}，以及一个 {@code produces=text/event-stream} 却返回一次性拼好字符串的
 * {@code /stream}——后者恒发 {@code RUN_STARTED + RUN_FINISHED}（{@code LoopRunDto} 根本没有 status
 * 字段），会把运行中/已失败的 run 谎报成 FINISHED。三者已删除：重复端点用 {@code /api/loop/runs}，
 * 需要真流式时应实现真正的增量推送而不是伪装 SSE。{@code {id}} 仍是 {@code loop_run.id}。
 */
@RestController
@RequestMapping("/api")
public class AgentRuntimeController {

  private final AgentRuntimeEventService eventService;
  private final LoopRuntimeService runtimeService;

  public AgentRuntimeController(
      AgentRuntimeEventService eventService, LoopRuntimeService runtimeService) {
    this.eventService = eventService;
    this.runtimeService = runtimeService;
  }

  @PostMapping("/events")
  @ResponseStatus(HttpStatus.CREATED)
  public AgentRuntimeEventService.EventRecord recordEvent(
      @CurrentUser CurrentUserPrincipal user,
      @Valid @RequestBody AgentRuntimeEventService.EventRequest request) {
    requireStudent(user);
    return eventService.recordEvent(user.userId(), request);
  }

  @PostMapping("/agent-runs/{id}/loop-states")
  @ResponseStatus(HttpStatus.CREATED)
  public LoopRuntimeService.LoopStateRecord saveLoopState(
      @CurrentUser CurrentUserPrincipal user,
      @PathVariable Long id,
      @Valid @RequestBody LoopStateRequest request) {
    requireStudent(user);
    return runtimeService.saveLoopState(
        user.userId(),
        id,
        request.loopType(),
        request.stateKey(),
        request.status(),
        request.state(),
        request.policyVersion(),
        request.traceId());
  }

  @PostMapping("/agent-runs/{id}/policy-decisions")
  @ResponseStatus(HttpStatus.CREATED)
  public LoopRuntimeService.PolicyDecisionRecord recordPolicyDecision(
      @CurrentUser CurrentUserPrincipal user,
      @PathVariable Long id,
      @Valid @RequestBody PolicyDecisionRequest request) {
    requireStudent(user);
    return runtimeService.recordPolicyDecision(
        user.userId(),
        id,
        request.decisionKey(),
        request.policyVersion(),
        request.riskLevel(),
        request.approvalRequired(),
        request.decision(),
        request.reason(),
        request.traceId());
  }

  private void requireStudent(CurrentUserPrincipal user) {
    if (user == null || user.role() != Role.STUDENT) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "仅学生可访问 Agent Runtime 控制面");
    }
  }

  public record LoopStateRequest(
      @NotBlank String loopType,
      @NotBlank String stateKey,
      @NotBlank String status,
      JsonNode state,
      @NotBlank String policyVersion,
      @NotBlank String traceId) {}

  public record PolicyDecisionRequest(
      @NotBlank String decisionKey,
      @NotBlank String policyVersion,
      @NotBlank String riskLevel,
      Boolean approvalRequired,
      JsonNode decision,
      String reason,
      @NotBlank String traceId) {}
}
