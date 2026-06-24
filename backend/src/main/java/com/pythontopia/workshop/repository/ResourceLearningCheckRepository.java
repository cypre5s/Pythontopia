package com.pythontopia.workshop.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.workshop.dto.ResourceCheckEvidenceType;
import com.pythontopia.workshop.dto.ResourceCheckStatus;
import com.pythontopia.workshop.dto.ResourceType;
import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ResourceLearningCheckRepository {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public ResourceLearningCheckRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public record CheckInsert(
      Long userId,
      Long resourceId,
      String kcCode,
      ResourceCheckEvidenceType evidenceType,
      String answerText,
      JsonNode operationEvidence,
      ResourceCheckStatus status,
      BigDecimal masteryDelta,
      ResourceType remediationType,
      String reason) {}

  public Long insert(CheckInsert insert) {
    Long id =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO resource_learning_check (
              user_id, resource_id, kc_code, evidence_type, answer_text, operation_evidence,
              status, mastery_delta, remediation_type, reason
            ) VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?) RETURNING id
            """,
            Long.class,
            insert.userId(),
            insert.resourceId(),
            insert.kcCode(),
            insert.evidenceType().name(),
            insert.answerText(),
            writeJson(insert.operationEvidence()),
            insert.status().name(),
            insert.masteryDelta(),
            insert.remediationType() == null ? null : insert.remediationType().name(),
            insert.reason());
    return id == null ? 0L : id;
  }

  private String writeJson(JsonNode node) {
    try {
      return objectMapper.writeValueAsString(node == null ? objectMapper.createObjectNode() : node);
    } catch (JsonProcessingException exception) {
      return "{}";
    }
  }
}
