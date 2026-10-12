package com.pythontopia.tutor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.tutor.dto.TutorWorkflowAgentStateDto;
import com.pythontopia.tutor.dto.TutorWorkflowCardDto;
import com.pythontopia.tutor.dto.TutorWorkflowSessionDto;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
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
      String actionPayloadJson) {}

  public record SubmissionFeedbackProjection(
      int seq,
      String verdict,
      String stateJson,
      String decisionJson,
      String actionPayloadJson) {}

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

  /**
   * Atomically projects one completed Graph run. The unique marker prevents duplicate terminal SSE
   * callbacks from replaying any of the projection writes.
   */
  @Transactional
  public boolean persistRunProjection(Long sessionId, RunProjection projection) {
    int claimed =
        jdbcTemplate.update(
            """
            INSERT INTO tutor_workflow_run_projection (session_id, run_id, seq, event)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (session_id, run_id) DO NOTHING
            """,
            sessionId,
            projection.runId(),
            projection.seq(),
            projection.event());
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
    return true;
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

  public List<TutorWorkflowCardDto> findCards(Long sessionId) {
    return jdbcTemplate.query(
        """
        SELECT seq, event, card_key, payload, created_at
        FROM tutor_workflow_card
        WHERE session_id = ?
        ORDER BY seq, id
        """,
        this::mapCard,
        sessionId);
  }

  public List<TutorWorkflowCardDto> findRecentCards(Long sessionId, int limit) {
    return jdbcTemplate.query(
        """
        SELECT seq, event, card_key, payload, created_at
        FROM (
          SELECT id, seq, event, card_key, payload, created_at
          FROM tutor_workflow_card
          WHERE session_id = ?
          ORDER BY seq DESC, id DESC
          LIMIT ?
        ) recent
        ORDER BY seq, id
        """,
        this::mapCard,
        sessionId,
        limit);
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
