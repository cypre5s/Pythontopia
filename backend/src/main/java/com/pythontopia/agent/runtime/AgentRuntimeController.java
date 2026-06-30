package com.pythontopia.agent.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.loop.LoopRunService;
import com.pythontopia.loop.LoopRuntimeService;
import com.pythontopia.loop.dto.LoopRunDto;
import com.pythontopia.loop.dto.LoopRunRecordRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AgentRuntimeController {

  private final AgentRuntimeEventService eventService;
  private final LoopRunService loopRunService;
  private final LoopRuntimeService runtimeService;
  private final ObjectMapper objectMapper;

  public AgentRuntimeController(
      AgentRuntimeEventService eventService,
      LoopRunService loopRunService,
      LoopRuntimeService runtimeService,
      ObjectMapper objectMapper) {
    this.eventService = eventService;
    this.loopRunService = loopRunService;
    this.runtimeService = runtimeService;
    this.objectMapper = objectMapper;
  }

  @PostMapping("/events")
  @ResponseStatus(HttpStatus.CREATED)
  public AgentRuntimeEventService.EventRecord recordEvent(
      @CurrentUser CurrentUserPrincipal user,
      @Valid @RequestBody AgentRuntimeEventService.EventRequest request) {
    requireStudent(user);
    return eventService.recordEvent(user.userId(), request);
  }

  @PostMapping("/agent-runs")
  @ResponseStatus(HttpStatus.CREATED)
  public LoopRunDto createAgentRun(
      @CurrentUser CurrentUserPrincipal user, @Valid @RequestBody LoopRunRecordRequest request) {
    requireStudent(user);
    return loopRunService.record(user.userId(), request);
  }

  @GetMapping("/agent-runs/{id}")
  public LoopRunDto getAgentRun(@CurrentUser CurrentUserPrincipal user, @PathVariable Long id) {
    requireStudent(user);
    return loopRunService.getForOwner(user.userId(), id);
  }

  @GetMapping(path = "/agent-runs/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public ResponseEntity<String> streamAgentRun(
      @CurrentUser CurrentUserPrincipal user, @PathVariable Long id) {
    requireStudent(user);
    LoopRunDto run = loopRunService.getForOwner(user.userId(), id);
    String body =
        sse("RUN_STARTED", eventPayload(run, "STARTED"))
            + sse("RUN_FINISHED", eventPayload(run, "FINISHED"));
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noCache())
        .contentType(MediaType.TEXT_EVENT_STREAM)
        .body(body);
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

  private ObjectNode eventPayload(LoopRunDto run, String status) {
    ObjectNode node = objectMapper.createObjectNode();
    node.put("runId", run.id());
    node.put("userId", run.userId());
    node.put("sessionId", run.sessionId());
    node.put("traceId", run.traceId());
    node.put("status", status);
    return node;
  }

  private String sse(String event, ObjectNode data) {
    try {
      return "event: " + event + "\n" + "data: " + objectMapper.writeValueAsString(data) + "\n\n";
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Unable to serialize agent runtime SSE event", ex);
    }
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
