package com.pythontopia.workshop.history;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class WorkshopChatHistoryRepository {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public WorkshopChatHistoryRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public Long insert(Long userId, WorkshopChatHistoryRecordRequest request) {
    GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(
        connection -> {
          PreparedStatement ps =
              connection.prepareStatement(
                  """
                  INSERT INTO workshop_chat_turn
                    (user_id, conversation_id, user_text, intent, response_kind, tool_label,
                     response_text, response_payload)
                  VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                  """,
                  new String[] {"id"});
          ps.setLong(1, userId);
          ps.setString(2, request.conversationId().trim());
          ps.setString(3, request.userText().trim());
          ps.setString(4, request.intent().trim());
          ps.setString(5, request.responseKind().trim());
          ps.setString(6, nullableTrim(request.toolLabel()));
          ps.setString(7, nullableTrim(request.responseText()));
          JsonNode payload =
              request.responsePayload() == null ? objectMapper.createObjectNode() : request.responsePayload();
          ps.setString(8, payload.toString());
          return ps;
        },
        keyHolder);
    Number key = keyHolder.getKey();
    if (key == null) {
      throw new IllegalStateException("workshop_chat_turn insert returned no key");
    }
    return key.longValue();
  }

  public List<WorkshopChatHistoryTurnDto> findByUser(Long userId, int limit) {
    int safeLimit = Math.max(1, Math.min(limit, 50));
    return jdbcTemplate.query(
        """
        SELECT *
        FROM (
          SELECT
            'workshop-' || id AS row_id,
            conversation_id,
            user_text,
            intent,
            response_kind,
            tool_label,
            response_text,
            response_payload,
            NULL::integer[] AS evidence_page_ids,
            NULL::varchar AS grounding_status,
            created_at
          FROM workshop_chat_turn
          WHERE user_id = ?
          UNION ALL
          SELECT
            'tutor-' || id AS row_id,
            COALESCE(conversation_id, 'tutor-' || id) AS conversation_id,
            question AS user_text,
            'ASK' AS intent,
            'answer' AS response_kind,
            '问答' AS tool_label,
            answer_md AS response_text,
            jsonb_build_object(
              'evidencePageIds', COALESCE(evidence_page_ids, ARRAY[]::integer[]),
              'groundingStatus', grounding_status
            ) AS response_payload,
            evidence_page_ids,
            grounding_status,
            created_at
          FROM tutor_session
          WHERE user_id = ?
        ) all_turns
        ORDER BY created_at DESC
        LIMIT ?
        """,
        this::mapDto,
        userId,
        userId,
        safeLimit);
  }

  private WorkshopChatHistoryTurnDto mapDto(ResultSet rs, int rowNum) throws SQLException {
    return new WorkshopChatHistoryTurnDto(
        rs.getString("row_id"),
        rs.getString("conversation_id"),
        rs.getString("user_text"),
        rs.getString("intent"),
        rs.getString("response_kind"),
        rs.getString("tool_label"),
        rs.getString("response_text"),
        readJson(rs.getString("response_payload")),
        readIntArray(rs.getArray("evidence_page_ids")),
        rs.getString("grounding_status"),
        toInstant(rs, "created_at"));
  }

  private JsonNode readJson(String raw) {
    if (raw == null || raw.isBlank()) {
      return objectMapper.createObjectNode();
    }
    try {
      return objectMapper.readTree(raw);
    } catch (Exception ex) {
      return objectMapper.createObjectNode();
    }
  }

  private List<Integer> readIntArray(Array array) throws SQLException {
    if (array == null) return List.of();
    Object raw = array.getArray();
    if (raw instanceof Integer[] values) {
      return Arrays.asList(values);
    }
    if (raw instanceof Object[] values) {
      return Arrays.stream(values).map(v -> ((Number) v).intValue()).toList();
    }
    return List.of();
  }

  private Instant toInstant(ResultSet rs, String column) throws SQLException {
    java.sql.Timestamp ts = rs.getTimestamp(column);
    return ts == null ? null : ts.toInstant();
  }

  private String nullableTrim(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
