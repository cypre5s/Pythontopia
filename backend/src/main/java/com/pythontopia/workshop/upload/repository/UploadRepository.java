package com.pythontopia.workshop.upload.repository;

import com.pythontopia.workshop.upload.entity.UploadStatus;
import com.pythontopia.workshop.upload.entity.WorkshopUpload;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * {@code workshop_upload} 的 JdbcTemplate 仓储。所有读写都按 {@code user_id} 隔离，杜绝越权读/删他人上传。
 * 列表查询不取 {@code extracted_text}（大文本），明细/取文本才读全行。
 */
@Repository
public class UploadRepository {

  private final JdbcTemplate jdbcTemplate;

  public UploadRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public WorkshopUpload insert(
      long userId,
      String filename,
      String ext,
      long byteSize,
      String extractedText,
      int charCount,
      int lineCount,
      UploadStatus status,
      String error) {
    return jdbcTemplate.queryForObject(
        """
        INSERT INTO workshop_upload (
          user_id, filename, ext, byte_size, extracted_text,
          char_count, line_count, status, error
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        RETURNING id, user_id, filename, ext, byte_size, extracted_text,
                  char_count, line_count, status, error, created_at
        """,
        this::mapFull,
        userId,
        filename,
        ext,
        byteSize,
        extractedText,
        charCount,
        lineCount,
        status.name(),
        error);
  }

  /** Serialize per-user quota checks so parallel uploads cannot exceed the storage limit. */
  public boolean canAcceptUpload(
      long userId, long incomingBytes, int maxFiles, long maxTotalBytes) {
    List<Long> owner =
        jdbcTemplate.query(
            "SELECT id FROM users WHERE id = ? FOR UPDATE",
            (rs, rowNum) -> rs.getLong(1),
            userId);
    if (owner.isEmpty()) {
      return false;
    }
    Boolean withinQuota =
        jdbcTemplate.queryForObject(
            "SELECT count(*) < ? AND coalesce(sum(byte_size), 0) + ? <= ?"
                + " FROM workshop_upload WHERE user_id = ?",
            Boolean.class,
            maxFiles,
            incomingBytes,
            maxTotalBytes,
            userId);
    return Boolean.TRUE.equals(withinQuota);
  }

  /** Cheap preflight; {@link #canAcceptUpload} repeats the check under the owner-row lock before insert. */
  public boolean hasUploadCapacity(
      long userId, long incomingBytes, int maxFiles, long maxTotalBytes) {
    Boolean withinQuota =
        jdbcTemplate.queryForObject(
            "SELECT (SELECT count(*) FROM workshop_upload WHERE user_id = ?) < ?"
                + " AND (SELECT coalesce(sum(byte_size), 0) FROM workshop_upload WHERE user_id = ?)"
                + " + ? <= ?",
            Boolean.class,
            userId,
            maxFiles,
            userId,
            incomingBytes,
            maxTotalBytes);
    return Boolean.TRUE.equals(withinQuota);
  }

  /** 列表：本人上传按时间倒序，不取 extracted_text（{@code extractedText} 置空串）。 */
  public List<WorkshopUpload> listByUser(long userId) {
    return jdbcTemplate.query(
        """
        SELECT id, user_id, filename, ext, byte_size,
               char_count, line_count, status, error, created_at
        FROM workshop_upload
        WHERE user_id = ?
        ORDER BY created_at DESC
        """,
        this::mapSummary,
        userId);
  }

  /** 明细：含 extracted_text，仅取本人这条。 */
  public Optional<WorkshopUpload> findByIdForUser(long userId, long id) {
    return jdbcTemplate
        .query(
            """
            SELECT id, user_id, filename, ext, byte_size, extracted_text,
                   char_count, line_count, status, error, created_at
            FROM workshop_upload
            WHERE id = ? AND user_id = ?
            """,
            this::mapFull,
            id,
            userId)
        .stream()
        .findFirst();
  }

  /** 删除本人这条，返回受影响行数（0 表示不存在或不归属，由 service 转 404）。 */
  public int deleteByIdForUser(long userId, long id) {
    return jdbcTemplate.update(
        "DELETE FROM workshop_upload WHERE id = ? AND user_id = ?", id, userId);
  }

  private WorkshopUpload mapFull(ResultSet rs, int rowNum) throws SQLException {
    return new WorkshopUpload(
        rs.getLong("id"),
        rs.getLong("user_id"),
        rs.getString("filename"),
        rs.getString("ext"),
        rs.getLong("byte_size"),
        rs.getString("extracted_text"),
        rs.getInt("char_count"),
        rs.getInt("line_count"),
        UploadStatus.valueOf(rs.getString("status")),
        rs.getString("error"),
        offset(rs, "created_at"));
  }

  private WorkshopUpload mapSummary(ResultSet rs, int rowNum) throws SQLException {
    return new WorkshopUpload(
        rs.getLong("id"),
        rs.getLong("user_id"),
        rs.getString("filename"),
        rs.getString("ext"),
        rs.getLong("byte_size"),
        "",
        rs.getInt("char_count"),
        rs.getInt("line_count"),
        UploadStatus.valueOf(rs.getString("status")),
        rs.getString("error"),
        offset(rs, "created_at"));
  }

  private OffsetDateTime offset(ResultSet rs, String column) throws SQLException {
    java.sql.Timestamp ts = rs.getTimestamp(column);
    return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
  }
}
