package com.pythontopia.loop;

import com.fasterxml.jackson.databind.JsonNode;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.loop.dto.LoopRunDto;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class LoopRuntimeService {

  private static final int MAX_KEY_LEN = 128;
  private static final int MAX_STATUS_LEN = 64;
  private static final int MAX_SUMMARY_LEN = 512;

  private final LoopRunRepository runRepository;
  private final LoopRuntimeRepository runtimeRepository;

  public LoopRuntimeService(
      LoopRunRepository runRepository, LoopRuntimeRepository runtimeRepository) {
    this.runRepository = runRepository;
    this.runtimeRepository = runtimeRepository;
  }

  public record PolicyRecord(
      Long id,
      String namespace,
      String policyKey,
      String policyVersion,
      String status,
      JsonNode config,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {}

  public record CheckpointRecord(
      Long id,
      Long loopRunId,
      int stepIndex,
      String status,
      JsonNode state,
      String resumeCursor,
      OffsetDateTime createdAt) {}

  public record ToolCallRecord(
      Long id,
      Long loopRunId,
      Long checkpointId,
      String toolName,
      JsonNode requestPayload,
      JsonNode responsePayload,
      String status,
      int latencyMs,
      OffsetDateTime createdAt) {}

  public record AgentRunRecord(
      Long id,
      Long loopRunId,
      String agentName,
      String status,
      String inputSummary,
      JsonNode outputArtifact,
      OffsetDateTime createdAt) {}

  public record EvalResultRecord(
      Long id,
      Long loopRunId,
      String datasetId,
      String itemId,
      String loopType,
      String policyVersion,
      JsonNode metrics,
      boolean passed,
      OffsetDateTime createdAt) {}

  public record RuntimeSnapshot(
      LoopRunDto loopRun,
      List<CheckpointRecord> checkpoints,
      List<ToolCallRecord> toolCalls,
      List<AgentRunRecord> agentRuns,
      List<EvalResultRecord> evalResults) {}

  public PolicyRecord registerPolicy(
      String namespace, String policyKey, String policyVersion, String status, JsonNode config) {
    return policy(
        runtimeRepository.upsertPolicy(
            requireText(namespace, "namespace"),
            requireText(policyKey, "policyKey"),
            requireText(policyVersion, "policyVersion"),
            requireText(status, "status"),
            config));
  }

  public CheckpointRecord saveCheckpoint(
      Long authUserId,
      Long loopRunId,
      int stepIndex,
      String status,
      JsonNode state,
      String resumeCursor) {
    requireOwnedRun(authUserId, loopRunId);
    if (stepIndex < 0) {
      throw badRequest("LOOP_CHECKPOINT_STEP_INVALID", "step_index 不能为负");
    }
    return checkpoint(
        runtimeRepository.upsertCheckpoint(
            loopRunId,
            stepIndex,
            requireText(status, "status"),
            state,
            resumeCursor == null ? "" : resumeCursor.strip()));
  }

  public ToolCallRecord recordToolCall(
      Long authUserId,
      Long loopRunId,
      Long checkpointId,
      String toolName,
      JsonNode requestPayload,
      JsonNode responsePayload,
      String status,
      int latencyMs) {
    requireOwnedRun(authUserId, loopRunId);
    if (latencyMs < 0) {
      throw badRequest("LOOP_TOOL_LATENCY_INVALID", "latency_ms 不能为负");
    }
    return toolCall(
        runtimeRepository.insertToolCall(
            loopRunId,
            checkpointId,
            requireText(toolName, "toolName"),
            requestPayload,
            responsePayload,
            requireText(status, "status"),
            latencyMs));
  }

  public AgentRunRecord recordAgentRun(
      Long authUserId,
      Long loopRunId,
      String agentName,
      String status,
      String inputSummary,
      JsonNode outputArtifact) {
    requireOwnedRun(authUserId, loopRunId);
    return agentRun(
        runtimeRepository.insertAgentRun(
            loopRunId,
            requireText(agentName, "agentName"),
            requireText(status, "status"),
            normalizeSummary(inputSummary),
            outputArtifact));
  }

  public EvalResultRecord recordEvalResult(
      Long authUserId,
      Long loopRunId,
      String datasetId,
      String itemId,
      String loopType,
      String policyVersion,
      JsonNode metrics,
      boolean passed) {
    requireOwnedRun(authUserId, loopRunId);
    return evalResult(
        runtimeRepository.insertEvalResult(
            loopRunId,
            requireText(datasetId, "datasetId"),
            requireText(itemId, "itemId"),
            requireText(loopType, "loopType"),
            requireText(policyVersion, "policyVersion"),
            metrics,
            passed));
  }

  public RuntimeSnapshot restoreSnapshot(Long authUserId, Long loopRunId) {
    LoopRunDto run = requireOwnedRun(authUserId, loopRunId);
    return new RuntimeSnapshot(
        run,
        runtimeRepository.checkpoints(loopRunId).stream().map(this::checkpoint).toList(),
        runtimeRepository.toolCalls(loopRunId).stream().map(this::toolCall).toList(),
        runtimeRepository.agentRuns(loopRunId).stream().map(this::agentRun).toList(),
        runtimeRepository.evalResults(loopRunId).stream().map(this::evalResult).toList());
  }

  private LoopRunDto requireOwnedRun(Long authUserId, Long loopRunId) {
    if (authUserId == null || loopRunId == null) {
      throw badRequest("LOOP_RUNTIME_REQUIRED", "user_id 与 loop_run_id 不能为空");
    }
    return runRepository
        .findById(loopRunId)
        .filter(run -> run.userId().equals(authUserId))
        .map(LoopRunDto::from)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND, "LOOP_RUN_NOT_FOUND", "闭环 run 不存在"));
  }

  private String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw badRequest("LOOP_RUNTIME_FIELD_REQUIRED", field + " 不能为空");
    }
    String trimmed = value.strip();
    if (trimmed.length() > MAX_KEY_LEN) {
      throw badRequest("LOOP_RUNTIME_FIELD_TOO_LONG", field + " 超过 " + MAX_KEY_LEN + " 字符");
    }
    if ("status".equals(field) && trimmed.length() > MAX_STATUS_LEN) {
      throw badRequest(
          "LOOP_RUNTIME_STATUS_TOO_LONG", "status 超过 " + MAX_STATUS_LEN + " 字符");
    }
    return trimmed;
  }

  private String normalizeSummary(String inputSummary) {
    if (inputSummary == null) {
      return "";
    }
    String trimmed = inputSummary.strip();
    return trimmed.length() <= MAX_SUMMARY_LEN ? trimmed : trimmed.substring(0, MAX_SUMMARY_LEN);
  }

  private BusinessException badRequest(String code, String message) {
    return new BusinessException(HttpStatus.BAD_REQUEST, code, message);
  }

  private PolicyRecord policy(LoopRuntimeRepository.PolicyRow row) {
    return new PolicyRecord(
        row.id(),
        row.namespace(),
        row.policyKey(),
        row.policyVersion(),
        row.status(),
        row.config(),
        row.createdAt(),
        row.updatedAt());
  }

  private CheckpointRecord checkpoint(LoopRuntimeRepository.CheckpointRow row) {
    return new CheckpointRecord(
        row.id(),
        row.loopRunId(),
        row.stepIndex(),
        row.status(),
        row.state(),
        row.resumeCursor(),
        row.createdAt());
  }

  private ToolCallRecord toolCall(LoopRuntimeRepository.ToolCallRow row) {
    return new ToolCallRecord(
        row.id(),
        row.loopRunId(),
        row.checkpointId(),
        row.toolName(),
        row.requestPayload(),
        row.responsePayload(),
        row.status(),
        row.latencyMs(),
        row.createdAt());
  }

  private AgentRunRecord agentRun(LoopRuntimeRepository.AgentRunRow row) {
    return new AgentRunRecord(
        row.id(),
        row.loopRunId(),
        row.agentName(),
        row.status(),
        row.inputSummary(),
        row.outputArtifact(),
        row.createdAt());
  }

  private EvalResultRecord evalResult(LoopRuntimeRepository.EvalResultRow row) {
    return new EvalResultRecord(
        row.id(),
        row.loopRunId(),
        row.datasetId(),
        row.itemId(),
        row.loopType(),
        row.policyVersion(),
        row.metrics(),
        row.passed(),
        row.createdAt());
  }
}
