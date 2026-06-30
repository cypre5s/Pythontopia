package com.pythontopia.loop;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LoopRuntimeRepository {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public LoopRuntimeRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public record PolicyRow(
      Long id,
      String namespace,
      String policyKey,
      String policyVersion,
      String status,
      JsonNode config,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {}

  public record CheckpointRow(
      Long id,
      Long loopRunId,
      int stepIndex,
      String status,
      JsonNode state,
      String resumeCursor,
      OffsetDateTime createdAt) {}

  public record ToolCallRow(
      Long id,
      Long loopRunId,
      Long checkpointId,
      String toolName,
      JsonNode requestPayload,
      JsonNode responsePayload,
      String status,
      int latencyMs,
      OffsetDateTime createdAt) {}

  public record AgentRunRow(
      Long id,
      Long loopRunId,
      String agentName,
      String status,
      String inputSummary,
      JsonNode outputArtifact,
      OffsetDateTime createdAt) {}

  public record EvalResultRow(
      Long id,
      Long loopRunId,
      String datasetId,
      String itemId,
      String loopType,
      String policyVersion,
      JsonNode metrics,
      boolean passed,
      OffsetDateTime createdAt) {}

  public PolicyRow upsertPolicy(
      String namespace, String policyKey, String policyVersion, String status, JsonNode config) {
    return jdbcTemplate.queryForObject(
        """
        INSERT INTO policy_registry (namespace, policy_key, policy_version, status, config)
        VALUES (?, ?, ?, ?, ?::jsonb)
        ON CONFLICT (namespace, policy_key, policy_version)
        DO UPDATE SET status = EXCLUDED.status, config = EXCLUDED.config, updated_at = now()
        RETURNING id, namespace, policy_key, policy_version, status, config::text AS config_text,
                  created_at, updated_at
        """,
        (rs, rowNum) -> mapPolicy(rs),
        namespace,
        policyKey,
        policyVersion,
        status,
        writeJson(config));
  }

  public Optional<PolicyRow> findPolicy(
      String namespace, String policyKey, String policyVersion) {
    return jdbcTemplate
        .query(
            """
            SELECT id, namespace, policy_key, policy_version, status, config::text AS config_text,
                   created_at, updated_at
            FROM policy_registry
            WHERE namespace = ? AND policy_key = ? AND policy_version = ?
            """,
            (rs, rowNum) -> mapPolicy(rs),
            namespace,
            policyKey,
            policyVersion)
        .stream()
        .findFirst();
  }

  public CheckpointRow upsertCheckpoint(
      Long loopRunId, int stepIndex, String status, JsonNode state, String resumeCursor) {
    return jdbcTemplate.queryForObject(
        """
        INSERT INTO loop_checkpoint (loop_run_id, step_index, status, state, resume_cursor)
        VALUES (?, ?, ?, ?::jsonb, ?)
        ON CONFLICT (loop_run_id, step_index)
        DO UPDATE SET status = EXCLUDED.status, state = EXCLUDED.state,
                      resume_cursor = EXCLUDED.resume_cursor, created_at = now()
        RETURNING id, loop_run_id, step_index, status, state::text AS state_text,
                  resume_cursor, created_at
        """,
        (rs, rowNum) -> mapCheckpoint(rs),
        loopRunId,
        stepIndex,
        status,
        writeJson(state),
        resumeCursor);
  }

  public ToolCallRow insertToolCall(
      Long loopRunId,
      Long checkpointId,
      String toolName,
      JsonNode requestPayload,
      JsonNode responsePayload,
      String status,
      int latencyMs) {
    return jdbcTemplate.queryForObject(
        """
        INSERT INTO agent_tool_call
            (loop_run_id, checkpoint_id, tool_name, request_payload, response_payload, status, latency_ms)
        VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)
        RETURNING id, loop_run_id, checkpoint_id, tool_name,
                  request_payload::text AS request_text,
                  response_payload::text AS response_text,
                  status, latency_ms, created_at
        """,
        (rs, rowNum) -> mapToolCall(rs),
        loopRunId,
        checkpointId,
        toolName,
        writeJson(requestPayload),
        writeJson(responsePayload),
        status,
        latencyMs);
  }

  public AgentRunRow insertAgentRun(
      Long loopRunId,
      String agentName,
      String status,
      String inputSummary,
      JsonNode outputArtifact) {
    return jdbcTemplate.queryForObject(
        """
        INSERT INTO agent_run (loop_run_id, agent_name, status, input_summary, output_artifact)
        VALUES (?, ?, ?, ?, ?::jsonb)
        RETURNING id, loop_run_id, agent_name, status, input_summary,
                  output_artifact::text AS output_text, created_at
        """,
        (rs, rowNum) -> mapAgentRun(rs),
        loopRunId,
        agentName,
        status,
        inputSummary,
        writeJson(outputArtifact));
  }

  public EvalResultRow insertEvalResult(
      Long loopRunId,
      String datasetId,
      String itemId,
      String loopType,
      String policyVersion,
      JsonNode metrics,
      boolean passed) {
    return jdbcTemplate.queryForObject(
        """
        INSERT INTO loop_eval_result
            (loop_run_id, dataset_id, item_id, loop_type, policy_version, metrics, passed)
        VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
        RETURNING id, loop_run_id, dataset_id, item_id, loop_type, policy_version,
                  metrics::text AS metrics_text, passed, created_at
        """,
        (rs, rowNum) -> mapEvalResult(rs),
        loopRunId,
        datasetId,
        itemId,
        loopType,
        policyVersion,
        writeJson(metrics),
        passed);
  }

  public List<CheckpointRow> checkpoints(Long loopRunId) {
    return jdbcTemplate.query(
        """
        SELECT id, loop_run_id, step_index, status, state::text AS state_text,
               resume_cursor, created_at
        FROM loop_checkpoint
        WHERE loop_run_id = ?
        ORDER BY step_index ASC, id ASC
        """,
        (rs, rowNum) -> mapCheckpoint(rs),
        loopRunId);
  }

  public List<ToolCallRow> toolCalls(Long loopRunId) {
    return jdbcTemplate.query(
        """
        SELECT id, loop_run_id, checkpoint_id, tool_name,
               request_payload::text AS request_text,
               response_payload::text AS response_text,
               status, latency_ms, created_at
        FROM agent_tool_call
        WHERE loop_run_id = ?
        ORDER BY id ASC
        """,
        (rs, rowNum) -> mapToolCall(rs),
        loopRunId);
  }

  public List<AgentRunRow> agentRuns(Long loopRunId) {
    return jdbcTemplate.query(
        """
        SELECT id, loop_run_id, agent_name, status, input_summary,
               output_artifact::text AS output_text, created_at
        FROM agent_run
        WHERE loop_run_id = ?
        ORDER BY id ASC
        """,
        (rs, rowNum) -> mapAgentRun(rs),
        loopRunId);
  }

  public List<EvalResultRow> evalResults(Long loopRunId) {
    return jdbcTemplate.query(
        """
        SELECT id, loop_run_id, dataset_id, item_id, loop_type, policy_version,
               metrics::text AS metrics_text, passed, created_at
        FROM loop_eval_result
        WHERE loop_run_id = ?
        ORDER BY id ASC
        """,
        (rs, rowNum) -> mapEvalResult(rs),
        loopRunId);
  }

  private PolicyRow mapPolicy(ResultSet rs) throws SQLException {
    return new PolicyRow(
        rs.getLong("id"),
        rs.getString("namespace"),
        rs.getString("policy_key"),
        rs.getString("policy_version"),
        rs.getString("status"),
        readJson(rs.getString("config_text")),
        offset(rs, "created_at"),
        offset(rs, "updated_at"));
  }

  private CheckpointRow mapCheckpoint(ResultSet rs) throws SQLException {
    return new CheckpointRow(
        rs.getLong("id"),
        rs.getLong("loop_run_id"),
        rs.getInt("step_index"),
        rs.getString("status"),
        readJson(rs.getString("state_text")),
        rs.getString("resume_cursor"),
        offset(rs, "created_at"));
  }

  private ToolCallRow mapToolCall(ResultSet rs) throws SQLException {
    long checkpointId = rs.getLong("checkpoint_id");
    return new ToolCallRow(
        rs.getLong("id"),
        rs.getLong("loop_run_id"),
        rs.wasNull() ? null : checkpointId,
        rs.getString("tool_name"),
        readJson(rs.getString("request_text")),
        readJson(rs.getString("response_text")),
        rs.getString("status"),
        rs.getInt("latency_ms"),
        offset(rs, "created_at"));
  }

  private AgentRunRow mapAgentRun(ResultSet rs) throws SQLException {
    return new AgentRunRow(
        rs.getLong("id"),
        rs.getLong("loop_run_id"),
        rs.getString("agent_name"),
        rs.getString("status"),
        rs.getString("input_summary"),
        readJson(rs.getString("output_text")),
        offset(rs, "created_at"));
  }

  private EvalResultRow mapEvalResult(ResultSet rs) throws SQLException {
    return new EvalResultRow(
        rs.getLong("id"),
        rs.getLong("loop_run_id"),
        rs.getString("dataset_id"),
        rs.getString("item_id"),
        rs.getString("loop_type"),
        rs.getString("policy_version"),
        readJson(rs.getString("metrics_text")),
        rs.getBoolean("passed"),
        offset(rs, "created_at"));
  }

  private JsonNode readJson(String raw) {
    if (raw == null || raw.isBlank()) {
      return objectMapper.createObjectNode();
    }
    try {
      return objectMapper.readTree(raw);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Invalid runtime JSON payload", ex);
    }
  }

  private String writeJson(JsonNode node) {
    if (node == null || node.isNull()) {
      return "{}";
    }
    return node.toString();
  }

  private OffsetDateTime offset(ResultSet rs, String column) throws SQLException {
    java.sql.Timestamp ts = rs.getTimestamp(column);
    return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
  }
}
