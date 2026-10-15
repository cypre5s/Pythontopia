package com.pythontopia.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pythontopia.intent.dto.WorkshopRouteDecision;
import com.pythontopia.loop.LoopRunService;
import com.pythontopia.loop.LoopRuntimeService;
import com.pythontopia.loop.dto.LoopRunDto;
import com.pythontopia.loop.dto.LoopRunRecordRequest;
import com.pythontopia.orchestrator.dto.OrchestratorTurnMessage;
import com.pythontopia.orchestrator.dto.OrchestratorTurnRequest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 把多任务 Orchestrator 的 AG-UI 事件旁路写入既有 loop runtime 表。任何记录失败均 fail-soft，
 * 不改变面向学生的 SSE 主链路。
 */
@Component
public class OrchestratorRunRecorder {

  private static final Logger log = LoggerFactory.getLogger(OrchestratorRunRecorder.class);
  private static final String LOOP_TYPE = "RESOURCE";

  private final LoopRunService loopRunService;
  private final LoopRuntimeService runtimeService;
  private final ObjectMapper objectMapper;

  public OrchestratorRunRecorder(
      LoopRunService loopRunService,
      LoopRuntimeService runtimeService,
      ObjectMapper objectMapper) {
    this.loopRunService = loopRunService;
    this.runtimeService = runtimeService;
    this.objectMapper = objectMapper;
  }

  /** 已预创建的可发现运行；GraphClient 会先发 runtime_run 事件，再转发上游 TaskPlan。 */
  public record RecordingHandle(
      Long loopRunId, String runtimeEventData, BiConsumer<String, String> onEvent) {}

  public RecordingHandle open(
      Long userId, OrchestratorTurnRequest request, String effectiveSessionId) {
    if (!isMultiTask(request)) {
      return null;
    }
    RecordingSession session = new RecordingSession(userId, request, effectiveSessionId);
    try {
      session.ensureRun();
      ObjectNode value = objectMapper.createObjectNode();
      value.put("loop_run_id", session.loopRun.id());
      value.put("route_id", session.decision.routeId());
      ObjectNode event = objectMapper.createObjectNode();
      event.put("name", "runtime_run");
      event.set("value", value);
      return new RecordingHandle(
          session.loopRun.id(), objectMapper.writeValueAsString(event), session::acceptFailSoft);
    } catch (Exception exception) {
      log.warn(
          "Orchestrator runtime open failed softly (route={}, errorType={})",
          request.routeDecision().routeId(),
          exception.getClass().getSimpleName());
      return null;
    }
  }

  /** 每条 SSE 流独占一个有状态回调；legacy PLAN 不建立 run，也不接入工具循环。 */
  public BiConsumer<String, String> onEvent(
      Long userId, OrchestratorTurnRequest request, String effectiveSessionId) {
    RecordingHandle handle = open(userId, request, effectiveSessionId);
    if (handle != null) {
      return handle.onEvent();
    }
    return isMultiTask(request) ? (event, data) -> {} : null;
  }

  private static boolean isMultiTask(OrchestratorTurnRequest request) {
    return request != null
        && request.routeDecision() != null
        && request.routeDecision().task() != null
        && "MULTI_TASK".equals(request.routeDecision().task().family());
  }

  private final class RecordingSession {

    private final Long userId;
    private final OrchestratorTurnRequest request;
    private final String sessionId;
    private final WorkshopRouteDecision decision;
    private final String policyVersion;
    private final Map<String, Integer> stepOrder = new LinkedHashMap<>();
    private final Map<String, JsonNode> planSteps = new HashMap<>();
    private final Set<String> agentAttemptsRecorded = new HashSet<>();
    private final Set<String> toolAttemptsRecorded = new HashSet<>();
    private final Set<String> completedStepIds = new HashSet<>();
    private LoopRunDto loopRun;

    private RecordingSession(
        Long userId, OrchestratorTurnRequest request, String effectiveSessionId) {
      this.userId = userId;
      this.request = request;
      this.sessionId = effectiveSessionId;
      this.decision = request.routeDecision();
      this.policyVersion = "workshop-route-v" + decision.version();
    }

    private synchronized void acceptFailSoft(String eventName, String data) {
      try {
        accept(eventName, data);
      } catch (RuntimeException exception) {
        log.warn(
            "Orchestrator runtime recording failed softly (route={}, event={}, errorType={})",
            decision.routeId(),
            eventName,
            exception.getClass().getSimpleName());
      }
    }

    private void accept(String eventName, String data) {
      JsonNode envelope = read(data);
      if ("STATE_DELTA".equals(eventName)) {
        onStateDelta(envelope.path("delta"));
        return;
      }
      if ("CUSTOM".equals(eventName)
          && "task_step_result".equals(envelope.path("name").asText())) {
        onStepResult(envelope.path("value"));
        return;
      }
      if ("RUN_FINISHED".equals(eventName) || "result".equals(eventName)) {
        JsonNode result = "RUN_FINISHED".equals(eventName) ? envelope.path("result") : envelope;
        onFinalResult(result);
      }
    }

    private void onStateDelta(JsonNode delta) {
      JsonNode steps = delta.path("plan_steps");
      if (!steps.isArray() || steps.isEmpty()) {
        return;
      }
      ensureRun();
      rememberStableSteps(steps);
      runtimeService.saveLoopState(
          userId,
          loopRun.id(),
          LOOP_TYPE,
          decision.routeId(),
          "RUNNING",
          delta,
          policyVersion,
          decision.routeId());
      for (JsonNode step : steps) {
        saveCheckpoint(step);
      }
    }

    private void ensureRun() {
      if (loopRun != null) {
        return;
      }
      WorkshopRouteDecision.Policies policies = decision.policies();
      int maxSteps = policies == null ? 4 : Math.max(1, Math.min(8, policies.maxAgentCalls()));
      List<String> tools =
          decision.tools() == null
              ? List.of()
              : normalizedTools(decision.tools().required());
      loopRun =
          loopRunService.record(
              userId,
              new LoopRunRecordRequest(
                  userId,
                  sessionId,
                  turnNumber(request.history()),
                  maxSteps,
                  0,
                  0,
                  tools,
                  true,
                  true,
                  0,
                  0,
                  decision.routeId()));
      runtimeService.recordPolicyDecision(
          userId,
          loopRun.id(),
          decision.routeId(),
          policyVersion,
          riskLevel(decision.task().complexity()),
          false,
          objectMapper.valueToTree(decision),
          "verified signed WorkshopRouteDecision",
          decision.routeId());
    }

    private void onStepResult(JsonNode value) {
      if (loopRun == null || value == null || value.isMissingNode()) {
        return;
      }
      persistStep(value);
    }

    private void onFinalResult(JsonNode result) {
      if (loopRun == null || result == null || result.isMissingNode()) {
        return;
      }
      JsonNode taskPlan = result.path("task_plan");
      if (!taskPlan.isObject()) {
        return;
      }
      JsonNode steps = taskPlan.path("steps");
      if (steps.isArray()) {
        rememberStableSteps(steps);
      }
      ObjectNode finalState = objectMapper.createObjectNode();
      finalState.set("routeDecision", objectMapper.valueToTree(decision));
      finalState.set("taskPlan", taskPlan);
      finalState.set("result", result);
      String finalStatus = normalizeStatus(taskPlan.path("status").asText("COMPLETED"));
      runtimeService.saveLoopState(
          userId,
          loopRun.id(),
          LOOP_TYPE,
          decision.routeId(),
          finalStatus,
          finalState,
          policyVersion,
          decision.routeId());

      Map<String, JsonNode> resultsByStep = dispatchResultsByStep(result.path("dispatch_results"));
      for (String stepId : stepOrder.keySet()) {
        JsonNode step = planSteps.get(stepId);
        JsonNode dispatch = resultsByStep.get(stepId);
        persistStep(mergedStepResult(stepId, step, dispatch));
      }
    }

    private void rememberStableSteps(JsonNode steps) {
      int index = 0;
      for (JsonNode step : steps) {
        String stepId = text(step, "id", "step_id");
        if (stepId.isBlank()) {
          index++;
          continue;
        }
        stepOrder.putIfAbsent(stepId, index);
        planSteps.put(stepId, step);
        index++;
      }
    }

    private void persistStep(JsonNode value) {
      String stepId = text(value, "step_id", "id");
      if (stepId.isBlank()) {
        return;
      }
      stepOrder.putIfAbsent(stepId, stepOrder.size());
      LoopRuntimeService.CheckpointRecord checkpoint = saveCheckpoint(value);
      JsonNode planStep = planSteps.get(stepId);
      String agent = firstNonBlank(text(value, "agent"), text(planStep, "agent"));
      String status = normalizeStatus(text(value, "status"));
      String summary = text(value, "summary");
      int attempt = Math.max(1, value.path("attempt").asInt(1));
      String attemptKey = stepId + ":" + attempt;
      if (!agent.isBlank() && agentAttemptsRecorded.add(attemptKey)) {
        runtimeService.recordAgentRun(
            userId,
            loopRun.id(),
            agent,
            status,
            summary.isBlank() ? request.message() : summary,
            artifactOrValue(value));
      }
      String tool = firstNonBlank(text(value, "tool_name"), text(planStep, "tool_name"));
      if (!tool.isBlank() && toolAttemptsRecorded.add(attemptKey)) {
        runtimeService.recordToolCall(
            userId,
            loopRun.id(),
            checkpoint == null ? null : checkpoint.id(),
            tool,
            planStep == null ? objectMapper.createObjectNode() : planStep,
            value,
            status,
            Math.max(0, value.path("elapsed_ms").asInt(0)));
      }
      if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
        completedStepIds.add(stepId);
      }
    }

    private LoopRuntimeService.CheckpointRecord saveCheckpoint(JsonNode step) {
      String stepId = text(step, "step_id", "id");
      int index = stepOrder.getOrDefault(stepId, stepOrder.size());
      ObjectNode recoverableState =
          step != null && step.isObject()
              ? ((ObjectNode) step).deepCopy()
              : objectMapper.createObjectNode();
      recoverableState.put("loopType", LOOP_TYPE);
      recoverableState.put("routeId", decision.routeId());
      recoverableState.put("nextAction", nextAction(stepId, text(step, "status")));
      recoverableState.put(
          "replayReason", "resume signed Workshop TaskPlan without expanding route authority");
      var evidenceRefs = recoverableState.putArray("evidenceRefs");
      evidenceRefs.add("route:" + decision.routeId());
      appendEvidenceRefs(evidenceRefs, step == null ? null : step.path("artifact"));
      LoopRuntimeService.CheckpointRecord saved =
          runtimeService.saveCheckpoint(
              userId,
              loopRun.id(),
              index,
              normalizeStatus(text(step, "status")),
              recoverableState,
              stepId);
      return saved;
    }

    private String nextAction(String stepId, String status) {
      String normalized = normalizeStatus(status);
      if (!"COMPLETED".equals(normalized) && !"FAILED".equals(normalized)) {
        return "execute:" + stepId;
      }
      int current = stepOrder.getOrDefault(stepId, -1);
      for (Map.Entry<String, Integer> entry : stepOrder.entrySet()) {
        if (entry.getValue() > current && !completedStepIds.contains(entry.getKey())) {
          return "execute:" + entry.getKey();
        }
      }
      return "review_result";
    }

    private void appendEvidenceRefs(
        com.fasterxml.jackson.databind.node.ArrayNode target, JsonNode artifact) {
      if (artifact == null || artifact.isMissingNode() || artifact.isNull()) {
        return;
      }
      for (String field : List.of("evidence_page_ids", "evidence_refs", "evidenceRefs")) {
        JsonNode values = artifact.path(field);
        if (!values.isArray()) {
          continue;
        }
        for (JsonNode value : values) {
          if (value.isValueNode() && !value.asText().isBlank()) {
            target.add(value.asText());
          }
        }
      }
    }

    private JsonNode mergedStepResult(String stepId, JsonNode step, JsonNode dispatch) {
      ObjectNode merged = objectMapper.createObjectNode();
      merged.put("step_id", stepId);
      merged.put("agent", firstNonBlank(text(dispatch, "agent"), text(step, "agent")));
      merged.put("tool_name", text(step, "tool_name"));
      boolean ok = dispatch != null && dispatch.path("ok").asBoolean(false);
      merged.put("status", ok ? "COMPLETED" : normalizeStatus(text(step, "status")));
      merged.put("summary", text(dispatch, "summary"));
      merged.put("elapsed_ms", dispatch == null ? 0 : dispatch.path("elapsed_ms").asInt(0));
      JsonNode artifact = dispatch == null ? null : dispatch.get("artifact");
      if (artifact != null) {
        merged.set("artifact", artifact);
      }
      return merged;
    }

    private Map<String, JsonNode> dispatchResultsByStep(JsonNode results) {
      Map<String, JsonNode> byStep = new HashMap<>();
      if (results.isArray()) {
        for (JsonNode result : results) {
          String stepId = text(result, "step_id", "id");
          if (!stepId.isBlank()) {
            byStep.put(stepId, result);
          }
        }
      }
      return byStep;
    }

    private JsonNode artifactOrValue(JsonNode value) {
      JsonNode artifact = value.get("artifact");
      return artifact == null ? value : artifact;
    }
  }

  private JsonNode read(String data) {
    try {
      return objectMapper.readTree(data == null ? "{}" : data);
    } catch (Exception exception) {
      throw new IllegalArgumentException("Invalid orchestrator SSE event payload", exception);
    }
  }

  private static int turnNumber(List<OrchestratorTurnMessage> history) {
    if (history == null) {
      return 0;
    }
    return (int) history.stream().filter(message -> message != null && "user".equals(message.role())).count();
  }

  private static List<String> normalizedTools(List<String> tools) {
    if (tools == null) {
      return List.of();
    }
    LinkedHashSet<String> normalized = new LinkedHashSet<>();
    for (String tool : tools) {
      if (tool != null && !tool.isBlank()) {
        normalized.add(tool.strip());
      }
    }
    return List.copyOf(normalized);
  }

  private static String riskLevel(String complexity) {
    if ("C3".equalsIgnoreCase(complexity)) {
      return "HIGH";
    }
    if ("C2".equalsIgnoreCase(complexity)) {
      return "MEDIUM";
    }
    return "LOW";
  }

  private static String normalizeStatus(String status) {
    if (status == null || status.isBlank()) {
      return "PENDING";
    }
    return switch (status.strip().toUpperCase()) {
      case "DONE", "OK", "SUCCESS" -> "COMPLETED";
      case "FAIL", "FAILED", "ERROR" -> "FAILED";
      default -> status.strip().toUpperCase();
    };
  }

  private static String text(JsonNode node, String... fields) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return "";
    }
    for (String field : fields) {
      JsonNode value = node.get(field);
      if (value != null && value.isTextual() && !value.asText().isBlank()) {
        return value.asText().strip();
      }
    }
    return "";
  }

  private static String firstNonBlank(String first, String second) {
    return first == null || first.isBlank() ? second == null ? "" : second : first;
  }
}
