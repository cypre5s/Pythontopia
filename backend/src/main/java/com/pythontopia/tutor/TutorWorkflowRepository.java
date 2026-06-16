package com.pythontopia.tutor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.tutor.dto.TutorWorkflowCardDto;
import com.pythontopia.tutor.dto.TutorWorkflowSessionDto;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/** 导学工作流投影（tutor_workflow_session / tutor_workflow_card）数据访问。 */
@Repository
public class TutorWorkflowRepository {

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
