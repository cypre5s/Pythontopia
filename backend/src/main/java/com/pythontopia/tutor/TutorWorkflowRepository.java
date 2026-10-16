package com.pythontopia.tutor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.tutor.dto.TutorWorkflowAgentStateDto;
import com.pythontopia.tutor.dto.TutorWorkflowCardCursorDto;
import com.pythontopia.tutor.dto.TutorWorkflowCardDto;
import com.pythontopia.tutor.dto.TutorWorkflowCardPageDto;
import com.pythontopia.tutor.dto.TutorWorkflowSessionDto;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 导学工作流投影（tutor_workflow_session / tutor_workflow_card）数据访问。 */
@Repository
public class TutorWorkflowRepository {

  public record RunProjection(
      UUID runId,
      String phase,
      int seq,
      String event,
      String stateJson,
      String decisionJson,
      String cardKey,
      String cardPayloadJson,
      String actionPayloadJson,
      String resultJson) {}

  public record SubmissionFeedbackProjection(
      int seq,
      String verdict,
      String stateJson,
      String decisionJson,
      String actionPayloadJson) {}

  public record AutoGenCheckpoint(
      UUID runId,
      String requestFingerprint,
      int stageIndex,
      String stageName,
      String checkpointJson,
      String status) {}

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public TutorWorkflowRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public Optional<TutorWorkflowSessionDto> findActive(Long userId, Long problemId) {
    List<TutorWorkflowSessionDto> rows =
        jdbcTemplate.query(
            """
            SELECT id, session_key, problem_id, phase, seq, status
            FROM tutor_workflow_session
            WHERE user_id = ? AND problem_id = ? AND status = 'ACTIVE'
            """,
            this::mapSession,
            userId,
            problemId);
    return rows.stream().findFirst();
  }

  public Optional<TutorWorkflowSessionDto> findBySessionKey(Long userId, String sessionKey) {
    List<TutorWorkflowSessionDto> rows =
        jdbcTemplate.query(
            """
            SELECT id, session_key, problem_id, phase, seq, status
            FROM tutor_workflow_session
            WHERE user_id = ? AND session_key = ? AND status = 'ACTIVE'
            """,
            this::mapSession,
            userId,
            sessionKey);
    return rows.stream().findFirst();
  }

  public TutorWorkflowSessionDto insertSession(Long userId, Long problemId, String sessionKey) {
    GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(
        connection -> {
          var ps =
              connection.prepareStatement(
                  """
                  INSERT INTO tutor_workflow_session (user_id, problem_id, session_key)
                  VALUES (?, ?, ?)
                  """,
                  new String[] {"id"});
          ps.setLong(1, userId);
          ps.setLong(2, problemId);
          ps.setString(3, sessionKey);
          return ps;
        },
        keyHolder);
    Number key = keyHolder.getKey();
    if (key == null) {
      throw new IllegalStateException("tutor_workflow_session insert returned no key");
    }
    return new TutorWorkflowSessionDto(key.longValue(), sessionKey, problemId, "READING", 0, "ACTIVE");
  }

  public void updateProgress(Long sessionId, String phase, int seq) {
    jdbcTemplate.update(
        """
        UPDATE tutor_workflow_session
        SET phase = ?, seq = GREATEST(seq, ?), updated_at = now()
        WHERE id = ?
        """,
        phase,
        seq,
        sessionId);
  }

  public void insertCard(Long sessionId, int seq, String event, String cardKey, String payloadJson) {
    jdbcTemplate.update(
        """
        INSERT INTO tutor_workflow_card (session_id, seq, event, card_key, payload)
        VALUES (?, ?, ?, ?, ?::jsonb)
        """,
        sessionId,
        seq,
        event,
        cardKey,
        payloadJson);
  }

  public Optional<TutorWorkflowAgentStateDto> findAgentState(Long sessionId) {
    List<TutorWorkflowAgentStateDto> rows =
        jdbcTemplate.query(
            """
            SELECT session_id, state_json::text, latest_decision_json::text
            FROM tutor_workflow_agent_state
            WHERE session_id = ?
            """,
            (rs, rowNum) ->
                new TutorWorkflowAgentStateDto(
                    rs.getLong("session_id"),
                    rs.getString("state_json"),
                    rs.getString("latest_decision_json")),
            sessionId);
    return rows.stream().findFirst();
  }

  public void upsertAgentState(Long sessionId, String stateJson, String decisionJson) {
    jdbcTemplate.update(
        """
        INSERT INTO tutor_workflow_agent_state (session_id, state_json, latest_decision_json)
        VALUES (?, ?::jsonb, ?::jsonb)
        ON CONFLICT (session_id) DO UPDATE
        SET state_json = EXCLUDED.state_json,
            latest_decision_json = EXCLUDED.latest_decision_json,
            updated_at = now()
        """,
        sessionId,
        stateJson,
        decisionJson);
  }

  public void insertActionEvent(
      Long sessionId, int seq, String eventType, String actionKey, String payloadJson) {
    jdbcTemplate.update(
        """
        INSERT INTO tutor_workflow_action_event (session_id, seq, event_type, action_key, payload)
        VALUES (?, ?, ?, ?, ?::jsonb)
        """,
        sessionId,
        seq,
        eventType,
        actionKey,
        payloadJson);
  }

  public int deleteActionEventsCreatedBefore(OffsetDateTime cutoff) {
    return jdbcTemplate.update(
        "DELETE FROM tutor_workflow_action_event WHERE created_at < ?", cutoff);
  }

  /**
   * Atomically projects one completed Graph run. The unique marker prevents duplicate terminal SSE
   * callbacks from replaying any of the projection writes.
   */
  @Transactional
  public boolean persistRunProjection(Long sessionId, RunProjection projection) {
    int claimed =
        jdbcTemplate.update(
            """
            INSERT INTO tutor_workflow_run_projection (session_id, run_id, seq, event, result_json)
            VALUES (?, ?, ?, ?, ?::jsonb)
            ON CONFLICT (session_id, run_id) DO NOTHING
            """,
            sessionId,
            projection.runId(),
            projection.seq(),
            projection.event(),
            projection.resultJson() == null ? "{}" : projection.resultJson());
    if (claimed == 0) {
      return false;
    }

    updateProgress(sessionId, projection.phase(), projection.seq());
    upsertAgentState(
        sessionId, projection.stateJson(), projection.decisionJson());
    if (projection.cardKey() != null && projection.cardPayloadJson() != null) {
      insertCard(
          sessionId,
          projection.seq(),
          projection.event(),
          projection.cardKey(),
          projection.cardPayloadJson());
    }
    String actionKey =
        projection.cardKey() == null ? projection.event() : projection.cardKey();
    String actionType = projection.cardKey() == null ? "WORKFLOW_RESULT" : "CARD_GENERATED";
    insertActionEvent(
        sessionId,
        projection.seq(),
        actionType,
        actionKey,
        projection.actionPayloadJson());
    jdbcTemplate.update(
        "UPDATE tutor_workflow_autogen_checkpoint SET status = 'COMPLETED',"
            + " checkpoint = '{}'::jsonb, updated_at = now()"
            + " WHERE session_id = ? AND run_id = ? AND status = 'RUNNING'",
        sessionId,
        projection.runId());
    return true;
  }

  /** Claims one active run, or resumes the latest interrupted run for the same request. */
  @Transactional
  public Optional<AutoGenCheckpoint> claimAutoGenRun(
      Long sessionId, UUID proposedRunId, String requestFingerprint) {
    jdbcTemplate.queryForObject(
        "SELECT id FROM tutor_workflow_session WHERE id = ? FOR UPDATE", Long.class, sessionId);
    jdbcTemplate.update(
        "UPDATE tutor_workflow_autogen_checkpoint SET status = 'INTERRUPTED', updated_at = now()"
            + " WHERE session_id = ? AND status = 'RUNNING'"
            + " AND updated_at < now() - INTERVAL '4 minutes'",
        sessionId);
    Integer active =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM tutor_workflow_autogen_checkpoint"
                + " WHERE session_id = ? AND status = 'RUNNING'",
            Integer.class,
            sessionId);
    if (active != null && active > 0) {
      return Optional.empty();
    }

    List<AutoGenCheckpoint> interrupted =
        jdbcTemplate.query(
            "SELECT run_id, request_fingerprint, stage_index, stage_name, checkpoint::text, status"
                + " FROM tutor_workflow_autogen_checkpoint"
                + " WHERE session_id = ? AND request_fingerprint = ? AND status = 'INTERRUPTED'"
                + " ORDER BY updated_at DESC, id DESC LIMIT 1 FOR UPDATE",
            this::mapAutoGenCheckpoint,
            sessionId,
            requestFingerprint);
    if (!interrupted.isEmpty()) {
      AutoGenCheckpoint previous = interrupted.getFirst();
      jdbcTemplate.update(
          "UPDATE tutor_workflow_autogen_checkpoint SET status = 'RUNNING', updated_at = now()"
              + " WHERE session_id = ? AND run_id = ? AND status = 'INTERRUPTED'",
          sessionId,
          previous.runId());
      return Optional.of(
          new AutoGenCheckpoint(
              previous.runId(),
              previous.requestFingerprint(),
              previous.stageIndex(),
              previous.stageName(),
              previous.checkpointJson(),
              "RUNNING"));
    }

    int claimed =
        jdbcTemplate.update(
            "INSERT INTO tutor_workflow_autogen_checkpoint"
                + " (session_id, run_id, request_fingerprint) VALUES (?, ?, ?)"
                + " ON CONFLICT DO NOTHING",
            sessionId,
            proposedRunId,
            requestFingerprint);
    if (claimed == 0) {
      return Optional.empty();
    }
    return Optional.of(
        new AutoGenCheckpoint(proposedRunId, requestFingerprint, -1, "", "{}", "RUNNING"));
  }

  public void saveAutoGenCheckpoint(
      Long sessionId,
      UUID runId,
      String requestFingerprint,
      int stageIndex,
      String stageName,
      String checkpointJson) {
    if (stageIndex < 0 || stageName == null || stageName.isBlank()) {
      throw new IllegalArgumentException("AutoGen checkpoint stage must be valid");
    }
    int updated =
        jdbcTemplate.update(
            "UPDATE tutor_workflow_autogen_checkpoint"
                + " SET stage_index = ?, stage_name = ?, checkpoint = ?::jsonb, updated_at = now()"
                + " WHERE session_id = ? AND run_id = ? AND request_fingerprint = ?"
                + " AND status = 'RUNNING' AND stage_index <= ?",
            stageIndex,
            stageName,
            checkpointJson,
            sessionId,
            runId,
            requestFingerprint,
            stageIndex);
    if (updated != 1) {
      throw new IllegalStateException("AutoGen checkpoint run or stage is no longer active");
    }
  }

  public boolean transitionAutoGenRun(Long sessionId, UUID runId, String status) {
    if (!List.of("INTERRUPTED", "FAILED").contains(status)) {
      throw new IllegalArgumentException("Unsupported AutoGen run status");
    }
    return jdbcTemplate.update(
            "UPDATE tutor_workflow_autogen_checkpoint SET status = ?,"
                + " checkpoint = CASE WHEN ? = 'FAILED' THEN '{}'::jsonb ELSE checkpoint END,"
                + " updated_at = now()"
                + " WHERE session_id = ? AND run_id = ? AND status = 'RUNNING'",
            status,
            status,
            sessionId,
            runId)
        == 1;
  }

  public Optional<AutoGenCheckpoint> findAutoGenCheckpoint(Long sessionId, UUID runId) {
    List<AutoGenCheckpoint> rows =
        jdbcTemplate.query(
            "SELECT run_id, request_fingerprint, stage_index, stage_name, checkpoint::text, status"
                + " FROM tutor_workflow_autogen_checkpoint WHERE session_id = ? AND run_id = ?",
            this::mapAutoGenCheckpoint,
            sessionId,
            runId);
    return rows.stream().findFirst();
  }

  public Optional<String> findAutoGenRunResult(Long sessionId, UUID runId) {
    List<String> rows =
        jdbcTemplate.query(
            "SELECT result_json::text FROM tutor_workflow_run_projection"
                + " WHERE session_id = ? AND run_id = ? AND result_json IS NOT NULL",
            (rs, rowNum) -> rs.getString(1),
            sessionId,
            runId);
    return rows.stream().findFirst();
  }

  /** Makes a failed request-key run claimable again after an explicit Web retry. */
  public boolean retryFailedAutoGenRun(Long sessionId, UUID runId, String requestFingerprint) {
    return jdbcTemplate.update(
            "UPDATE tutor_workflow_autogen_checkpoint SET status = 'INTERRUPTED',"
                + " stage_index = -1, stage_name = '', checkpoint = '{}'::jsonb, updated_at = now()"
                + " WHERE session_id = ? AND run_id = ? AND request_fingerprint = ?"
                + " AND status = 'FAILED'",
            sessionId,
            runId,
            requestFingerprint)
        == 1;
  }

  private AutoGenCheckpoint mapAutoGenCheckpoint(ResultSet rs, int rowNum) throws SQLException {
    return new AutoGenCheckpoint(
        rs.getObject("run_id", UUID.class),
        rs.getString("request_fingerprint"),
        rs.getInt("stage_index"),
        rs.getString("stage_name"),
        rs.getString("checkpoint"),
        rs.getString("status"));
  }

  /** Atomically stores a submission feedback checkpoint and its event log entry. */
  @Transactional
  public void persistSubmissionFeedback(
      Long sessionId, SubmissionFeedbackProjection projection) {
    upsertAgentState(sessionId, projection.stateJson(), projection.decisionJson());
    insertActionEvent(
        sessionId,
        projection.seq(),
        "SUBMISSION_OBSERVED",
        projection.verdict(),
        projection.actionPayloadJson());
  }

  public TutorWorkflowCardPageDto findLatestCardPage(Long sessionId, int pageSize) {
    List<StoredCard> rows =
        jdbcTemplate.query(
            """
            SELECT id, seq, event, card_key, payload, created_at
            FROM tutor_workflow_card
            WHERE session_id = ?
            ORDER BY seq DESC, id DESC
            LIMIT ?
            """,
            this::mapStoredCard,
            sessionId,
            pageSize + 1);
    return toCardPage(rows, pageSize);
  }

  public List<TutorWorkflowCardDto> findRecentCards(Long sessionId, int limit) {
    return findLatestCardPage(sessionId, limit).cards();
  }

  public Optional<TutorWorkflowCardDto> findCardByStoredId(Long sessionId, String cardId) {
    return jdbcTemplate
        .query(
            """
            SELECT seq, event, card_key, payload, created_at
            FROM tutor_workflow_card
            WHERE session_id = ? AND payload->>'card_id' = ?
            ORDER BY seq, id
            LIMIT 1
            """,
            this::mapCard,
            sessionId,
            cardId)
        .stream()
        .findFirst();
  }

  public Optional<TutorWorkflowCardDto> findFirstCardBySeq(Long sessionId, int seq) {
    return jdbcTemplate
        .query(
            """
            SELECT seq, event, card_key, payload, created_at
            FROM tutor_workflow_card
            WHERE session_id = ? AND seq = ?
            ORDER BY id
            LIMIT 1
            """,
            this::mapCard,
            sessionId,
            seq)
        .stream()
        .findFirst();
  }

  public TutorWorkflowCardPageDto findCardsBefore(
      Long sessionId, TutorWorkflowCardCursorDto cursor, int pageSize) {
    List<StoredCard> rows =
        jdbcTemplate.query(
            """
            SELECT id, seq, event, card_key, payload, created_at
            FROM tutor_workflow_card
            WHERE session_id = ? AND (seq < ? OR (seq = ? AND id < ?))
            ORDER BY seq DESC, id DESC
            LIMIT ?
            """,
            this::mapStoredCard,
            sessionId,
            cursor.seq(),
            cursor.seq(),
            cursor.id(),
            pageSize + 1);
    return toCardPage(rows, pageSize);
  }

  private TutorWorkflowCardPageDto toCardPage(List<StoredCard> rows, int pageSize) {
    boolean hasOlder = rows.size() > pageSize;
    List<StoredCard> page =
        new ArrayList<>(rows.subList(0, Math.min(rows.size(), pageSize)));
    TutorWorkflowCardCursorDto nextCursor = null;
    if (hasOlder && !page.isEmpty()) {
      StoredCard oldest = page.getLast();
      nextCursor = new TutorWorkflowCardCursorDto(oldest.card().seq(), oldest.id());
    }
    Collections.reverse(page);
    return new TutorWorkflowCardPageDto(
        page.stream().map(StoredCard::card).toList(), nextCursor);
  }

  private StoredCard mapStoredCard(ResultSet rs, int rowNum) throws SQLException {
    return new StoredCard(rs.getLong("id"), mapCard(rs, rowNum));
  }

  private record StoredCard(long id, TutorWorkflowCardDto card) {
  }

  private TutorWorkflowSessionDto mapSession(ResultSet rs, int rowNum) throws SQLException {
    return new TutorWorkflowSessionDto(
        rs.getLong("id"),
        rs.getString("session_key"),
        rs.getLong("problem_id"),
        rs.getString("phase"),
        rs.getInt("seq"),
        rs.getString("status"));
  }

  private TutorWorkflowCardDto mapCard(ResultSet rs, int rowNum) throws SQLException {
    JsonNode payload;
    try {
      payload = objectMapper.readTree(rs.getString("payload"));
    } catch (Exception e) {
      throw new SQLException("Invalid tutor_workflow_card payload JSON", e);
    }
    Instant created = rs.getTimestamp("created_at").toInstant();
    return new TutorWorkflowCardDto(
        rs.getInt("seq"),
        rs.getString("event"),
        rs.getString("card_key"),
        payload,
        created.atOffset(ZoneOffset.UTC));
  }
}
