package com.pythontopia.tutor;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Tutor workflow 私有图片仓储；所有读取同时约束用户、会话与有效期。 */
@Repository
public class TutorWorkflowAttachmentRepository {

  private final JdbcTemplate jdbcTemplate;

  public TutorWorkflowAttachmentRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /** Serialize uploads per owner so concurrent requests cannot race the aggregate quotas. */
  public void lockOwnerUploads(long userId) {
    List<Long> rows =
        jdbcTemplate.query(
            "SELECT id FROM users WHERE id = ? FOR UPDATE", (rs, rowNum) -> rs.getLong(1), userId);
    if (rows.isEmpty()) {
      throw new IllegalStateException("tutor workflow attachment owner no longer exists");
    }
  }

  public TutorWorkflowAttachmentUsage activeUsage(long userId, long sessionId) {
    return jdbcTemplate.queryForObject(
        """
        SELECT
          count(*) FILTER (WHERE a.session_id = ?) AS session_count,
          coalesce(sum(octet_length(a.content)) FILTER (WHERE a.session_id = ?), 0) AS session_bytes,
          count(*) AS user_count,
          coalesce(sum(octet_length(a.content)), 0) AS user_bytes
        FROM tutor_workflow_attachment a
        JOIN tutor_workflow_session s ON s.id = a.session_id
        WHERE s.user_id = ? AND a.expires_at > now()
        """,
        (rs, rowNum) ->
            new TutorWorkflowAttachmentUsage(
                rs.getLong("session_count"),
                rs.getLong("session_bytes"),
                rs.getLong("user_count"),
                rs.getLong("user_bytes")),
        sessionId,
        sessionId,
        userId);
  }

  public void insert(
      long sessionId,
      UUID attachmentId,
      String contentType,
      byte[] content,
      Instant expiresAt) {
    jdbcTemplate.update(
        """
        INSERT INTO tutor_workflow_attachment (id, session_id, content_type, content, expires_at)
        VALUES (?, ?, ?, ?, ?)
        """,
        attachmentId,
        sessionId,
        contentType,
        content,
        Timestamp.from(expiresAt));
  }

  public Optional<TutorWorkflowStoredImage> findActiveForOwnerSession(
      long userId, long sessionId, UUID attachmentId) {
    return jdbcTemplate
        .query(
            """
            SELECT a.content_type, a.content
            FROM tutor_workflow_attachment a
            JOIN tutor_workflow_session s ON s.id = a.session_id
            WHERE a.id = ? AND a.session_id = ? AND s.user_id = ? AND s.status = 'ACTIVE'
              AND a.expires_at > now()
            """,
            (rs, rowNum) ->
                new TutorWorkflowStoredImage(rs.getString("content_type"), rs.getBytes("content")),
            attachmentId,
            sessionId,
            userId)
        .stream()
        .findFirst();
  }

  public int deleteExpired() {
    return jdbcTemplate.update("DELETE FROM tutor_workflow_attachment WHERE expires_at <= now()");
  }
}
