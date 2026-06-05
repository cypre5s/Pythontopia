package com.pythontopia.agent.run;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;

@Component
public class ResourceRunRecorder {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public ResourceRunRecorder(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  /** 便捷重载：省略 graph_trace 与 correlation_id，且不返回 run id。 */
  public void record(
      Long userId,
      String agentName,
      String scenario,
      Object inputSummary,
      Object outputArtifact,
      List<Long> evidencePageIds,
      Object safetyResult,
      Object groundingResult,
      RunStatus status) {
    record(
        userId,
        agentName,
        scenario,
        inputSummary,
        outputArtifact,
        evidencePageIds,
        safetyResult,
        groundingResult,
        null,
        null,
        status);
  }

  /**
   * 持久化一次 agent 运行到 resource_run 表（agent_name、evidence_page_ids、safety_result、
   * grounding_result 等契约字段），返回自增主键 id 作为 run id。
   */
  public Long record(
      Long userId,
      String agentName,
      String scenario,
      Object inputSummary,
      Object outputArtifact,
      List<Long> evidencePageIds,
      Object safetyResult,
      Object groundingResult,
      Object graphTrace,
      UUID correlationId,
      RunStatus status) {
    GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(
        connection -> {
          PreparedStatement ps =
              connection.prepareStatement(
                  """
                  INSERT INTO resource_run (
                    user_id, agent_name, scenario, input_summary, output_artifact,
                    evidence_page_ids, safety_result, grounding_result, status,
                    correlation_id, graph_trace
                  )
                  VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?, ?::jsonb, ?::jsonb, ?, ?::uuid, ?::jsonb)
                  """,
                  new String[] {"id"});
          ps.setLong(1, userId);
          ps.setString(2, agentName);
          ps.setString(3, scenario);
          ps.setString(4, writeJson(inputSummary));
          ps.setString(5, writeJson(outputArtifact));
          Array evidenceArray =
              connection.createArrayOf("BIGINT", evidencePageIds.toArray(Long[]::new));
          ps.setArray(6, evidenceArray);
          ps.setString(7, writeJson(safetyResult));
          ps.setString(8, writeJson(groundingResult));
          ps.setString(9, status.name());
          if (correlationId == null) {
            ps.setNull(10, java.sql.Types.VARCHAR);
          } else {
            ps.setString(10, correlationId.toString());
          }
          ps.setString(11, writeJson(graphTrace));
          return ps;
        },
        keyHolder);
    Number key = keyHolder.getKey();
    return key == null ? null : key.longValue();
  }

  private String writeJson(Object value) {
    try {
      Object jsonValue = value == null ? objectMapper.createObjectNode() : value;
      if (jsonValue instanceof List<?> list && list.isEmpty()) {
        jsonValue = objectMapper.createArrayNode();
      }
      if (jsonValue instanceof ArrayNode) {
        return objectMapper.writeValueAsString(jsonValue);
      }
      return objectMapper.writeValueAsString(jsonValue);
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to serialize resource run payload", exception);
    }
  }
}
