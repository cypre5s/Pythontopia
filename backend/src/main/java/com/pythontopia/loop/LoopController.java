package com.pythontopia.loop;

import com.fasterxml.jackson.databind.JsonNode;
import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.loop.dto.LoopRunDto;
import com.pythontopia.loop.dto.LoopRunRecordRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 有界 Agentic Loop run 审计入口。{@code POST /runs} 在每轮闭环结束后落库审计（供 graph S1 回写，
 * Phase 2 接线时随附学生 JWT 或由后端编排器在进程内调用 {@link LoopRunService}）；
 * {@code GET /runs/{id}} 取本人某轮闭环审计详情。两端 {@code requireStudent} + 按 owner 隔离（§8.6）。
 */
@RestController
@RequestMapping("/api/loop")
public class LoopController {

  private final LoopRunService service;
  private final LoopRuntimeService runtimeService;

  public LoopController(LoopRunService service, LoopRuntimeService runtimeService) {
    this.service = service;
    this.runtimeService = runtimeService;
  }

  @PostMapping("/runs")
  @ResponseStatus(HttpStatus.CREATED)
  public LoopRunDto recordRun(
      @CurrentUser CurrentUserPrincipal user, @Valid @RequestBody LoopRunRecordRequest request) {
    requireStudent(user);
    return service.record(user.userId(), request);
  }

  @GetMapping("/runs/{id}")
  public LoopRunDto getRun(@CurrentUser CurrentUserPrincipal user, @PathVariable Long id) {
    requireStudent(user);
    return service.getForOwner(user.userId(), id);
  }

  @PostMapping("/runs/{id}/checkpoints")
  @ResponseStatus(HttpStatus.CREATED)
  public LoopRuntimeService.CheckpointRecord recordCheckpoint(
      @CurrentUser CurrentUserPrincipal user,
      @PathVariable Long id,
      @Valid @RequestBody CheckpointRequest request) {
    requireStudent(user);
    return runtimeService.saveCheckpoint(
        user.userId(), id, request.stepIndex(), request.status(), request.state(), request.resumeCursor());
  }

  @PostMapping("/runs/{id}/tool-calls")
  @ResponseStatus(HttpStatus.CREATED)
  public LoopRuntimeService.ToolCallRecord recordToolCall(
      @CurrentUser CurrentUserPrincipal user,
      @PathVariable Long id,
      @Valid @RequestBody ToolCallRequest request) {
    requireStudent(user);
    return runtimeService.recordToolCall(
        user.userId(),
        id,
        request.checkpointId(),
        request.toolName(),
        request.requestPayload(),
        request.responsePayload(),
        request.status(),
        request.latencyMs());
  }

  @PostMapping("/runs/{id}/agent-runs")
  @ResponseStatus(HttpStatus.CREATED)
  public LoopRuntimeService.AgentRunRecord recordAgentRun(
      @CurrentUser CurrentUserPrincipal user,
      @PathVariable Long id,
      @Valid @RequestBody AgentRunRequest request) {
    requireStudent(user);
    return runtimeService.recordAgentRun(
        user.userId(),
        id,
        request.agentName(),
        request.status(),
        request.inputSummary(),
        request.outputArtifact());
  }

  @PostMapping("/runs/{id}/eval-results")
  @ResponseStatus(HttpStatus.CREATED)
  public LoopRuntimeService.EvalResultRecord recordEvalResult(
      @CurrentUser CurrentUserPrincipal user,
      @PathVariable Long id,
      @Valid @RequestBody EvalResultRequest request) {
    requireStudent(user);
    return runtimeService.recordEvalResult(
        user.userId(),
        id,
        request.datasetId(),
        request.itemId(),
        request.loopType(),
        request.policyVersion(),
        request.metrics(),
        request.passed());
  }

  @GetMapping("/runs/{id}/runtime")
  public LoopRuntimeService.RuntimeSnapshot runtimeSnapshot(
      @CurrentUser CurrentUserPrincipal user, @PathVariable Long id) {
    requireStudent(user);
    return runtimeService.restoreSnapshot(user.userId(), id);
  }

  private void requireStudent(CurrentUserPrincipal user) {
    if (user == null || user.role() != Role.STUDENT) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "仅学生可记录/查看闭环 run 审计");
    }
  }

  public record CheckpointRequest(
      @NotNull @Min(0) Integer stepIndex,
      @NotBlank String status,
      JsonNode state,
      String resumeCursor) {}

  public record ToolCallRequest(
      Long checkpointId,
      @NotBlank String toolName,
      JsonNode requestPayload,
      JsonNode responsePayload,
      @NotBlank String status,
      @NotNull @Min(0) Integer latencyMs) {}

  public record AgentRunRequest(
      @NotBlank String agentName,
      @NotBlank String status,
      String inputSummary,
      JsonNode outputArtifact) {}

  public record EvalResultRequest(
      @NotBlank String datasetId,
      @NotBlank String itemId,
      @NotBlank String loopType,
      @NotBlank String policyVersion,
      JsonNode metrics,
      @NotNull Boolean passed) {}
}
