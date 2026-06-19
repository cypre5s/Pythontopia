package com.pythontopia.tutor;

import com.pythontopia.tutor.dto.TutorSessionDto;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class TutorSessionRepository {

  private final JdbcTemplate jdbcTemplate;

  public TutorSessionRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Long insert(Long userId, String question, String answerMd,
      List<Integer> evidencePageIds, String groundingStatus, String conversationId) {
    GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement ps = connection.prepareStatement(
          """
          INSERT INTO tutor_session
            (user_id, question, answer_md, evidence_page_ids, grounding_status, conversation_id)
          VALUES (?, ?, ?, ?, ?, ?)
          """,
          new String[]{"id"});
      ps.setLong(1, userId);
      ps.setString(2, question);
      ps.setString(3, answerMd);
      if (evidencePageIds != null && !evidencePageIds.isEmpty()) {
        Array pgArray = connection.createArrayOf("INTEGER", evidencePageIds.toArray(Integer[]::new));
        ps.setArray(4, pgArray);
      } else {
        ps.setNull(4, java.sql.Types.ARRAY);
      }
      ps.setString(5, groundingStatus);
      ps.setString(6, conversationId);
      return ps;
    }, keyHolder);
    Number key = keyHolder.getKey();
    if (key == null) {
      throw new IllegalStateException("tutor_session insert returned no key");
    }
    return key.longValue();
  }

  public List<TutorSessionDto> findByUser(Long userId, int limit) {
    int safeLimit = Math.max(1, Math.min(limit, 50));
    return jdbcTemplate.query(
        """
        SELECT id, question, answer_md, evidence_page_ids, grounding_status, conversation_id, created_at
        FROM tutor_session
        WHERE user_id = ?
        ORDER BY created_at DESC
        LIMIT ?
        """,
        this::mapDto,
        userId, safeLimit);
  }

  /**
   * 取某会话窗口内的全部问答（按时间升序），用于多轮 thread 展示与「真·多轮记忆」上下文注入。
   * 同时按 user_id 过滤，杜绝跨用户读他人会话。
   */
  public List<TutorSessionDto> findByConversation(Long userId, String conversationId, int limit) {
    int safeLimit = Math.max(1, Math.min(limit, 50));
    return jdbcTemplate.query(
        """
        SELECT id, question, answer_md, evidence_page_ids, grounding_status, conversation_id, created_at
        FROM (
          SELECT id, question, answer_md, evidence_page_ids, grounding_status, conversation_id, created_at
          FROM tutor_session
          WHERE user_id = ? AND conversation_id = ?
          ORDER BY created_at DESC
          LIMIT ?
        ) recent
        ORDER BY created_at ASC
        """,
        this::mapDto,
        userId, conversationId, safeLimit);
  }

  private TutorSessionDto mapDto(ResultSet rs, int rowNum) throws SQLException {
    return new TutorSessionDto(
        rs.getLong("id"),
        rs.getString("question"),
        rs.getString("answer_md"),
        readIntArray(rs.getArray("evidence_page_ids")),
        rs.getString("grounding_status"),
        rs.getString("conversation_id"),
        toInstant(rs, "created_at"));
  }

  private List<Integer> readIntArray(Array array) throws SQLException {
    if (array == null) return List.of();
    Object raw = array.getArray();
    if (raw instanceof Integer[] values) {
      return Arrays.asList(values);
    }
    if (raw instanceof Object[] values) {
      return Arrays.stream(values)
          .map(v -> ((Number) v).intValue())
          .toList();
    }
    return List.of();
  }

  private Instant toInstant(ResultSet rs, String column) throws SQLException {
    java.sql.Timestamp ts = rs.getTimestamp(column);
    return ts == null ? null : ts.toInstant();
  }
}
