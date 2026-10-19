package com.pythontopia.workshop.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.workshop.dto.ResourceCheckEvidenceType;
import com.pythontopia.workshop.dto.ResourceCheckResponse;
import com.pythontopia.workshop.dto.ResourceCheckStatus;
import com.pythontopia.workshop.dto.ResourceType;
import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
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
      String reason,
      String idempotencyKey,
      String requestHash) {}

  public record StoredIdempotencyResponse(String requestHash, String responsePayload) {}

  public void acquireIdempotencyLock(Long userId, Long resourceId, String idempotencyKey) {
    String lockKey = userId + ":" + resourceId + ":" + idempotencyKey;
    jdbcTemplate.query(
        "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
        (RowCallbackHandler) resultSet -> {},
        lockKey);
  }

  public StoredIdempotencyResponse findIdempotency(
      Long userId, Long resourceId, String idempotencyKey) {
    var rows =
        jdbcTemplate.query(
            """
            SELECT request_hash, response_payload::text AS response_payload
            FROM resource_learning_check
            WHERE user_id = ? AND resource_id = ? AND idempotency_key = ?
            """,
            (resultSet, rowNumber) ->
                new StoredIdempotencyResponse(
                    resultSet.getString("request_hash"),
                    resultSet.getString("response_payload")),
            userId,
            resourceId,
            idempotencyKey);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  public void completeIdempotency(
      Long userId,
      Long resourceId,
      String idempotencyKey,
      Long checkId,
      ResourceCheckResponse response) {
    int updated =
        jdbcTemplate.update(
            """
            UPDATE resource_learning_check
            SET response_payload = ?::jsonb
            WHERE id = ? AND user_id = ? AND resource_id = ? AND idempotency_key = ?
              AND response_payload IS NULL
            """,
            writeJson(response),
            checkId,
            userId,
            resourceId,
            idempotencyKey);
    if (updated != 1) {
      throw new IllegalStateException("Resource check idempotency response was not persisted");
    }
  }

  public Long insert(CheckInsert insert) {
    Long id =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO resource_learning_check (
              user_id, resource_id, kc_code, evidence_type, answer_text, operation_evidence,
              status, mastery_delta, remediation_type, reason, idempotency_key, request_hash
            ) VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?) RETURNING id
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
            insert.reason(),
            insert.idempotencyKey(),
            insert.requestHash());
    return id == null ? 0L : id;
  }

  public int countFailedChecks(Long userId, Long resourceId, String kcCode) {
    Integer count =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM resource_learning_check
            WHERE user_id = ? AND resource_id = ? AND kc_code = ? AND status = 'FAILED'
            """,
            Integer.class,
            userId,
            resourceId,
            kcCode);
    return count == null ? 0 : count;
  }

  private String writeJson(JsonNode node) {
    try {
      return objectMapper.writeValueAsString(node == null ? objectMapper.createObjectNode() : node);
    } catch (JsonProcessingException exception) {
      return "{}";
    }
  }

  private String writeJson(ResourceCheckResponse response) {
    try {
      return objectMapper.writeValueAsString(response);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Could not serialize the stored resource check response", exception);
    }
  }
}
