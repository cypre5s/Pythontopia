package com.pythontopia.browse.repository;

import com.pythontopia.browse.entity.BrowseRunEntity;
import com.pythontopia.browse.entity.BrowseRunStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * {@code browse_run} 的 JdbcTemplate 仓储。所有读都按 {@code user_id} 隔离，杜绝越权读他人浏览会话
 * （§8.6 机密性：按 user_id 隔离）。
 */
@Repository
public class BrowseRunRepository {

  private final JdbcTemplate jdbcTemplate;

  public BrowseRunRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /** 插入一条会话头（终态由调用方算好），RETURNING 完整行（含自增 id 与 created_at）。 */
  public BrowseRunEntity insert(
      long userId,
      String goal,
      BrowseRunStatus status,
      String finalSummary,
      boolean truncated,
      String error) {
    return jdbcTemplate.queryForObject(
        """
        INSERT INTO browse_run (user_id, goal, status, final_summary, truncated, error)
        VALUES (?, ?, ?, ?, ?, ?)
        RETURNING id, user_id, goal, status, final_summary, truncated, error, created_at
        """,
        this::map,
        userId,
        goal,
        status.name(),
        finalSummary,
        truncated,
        error);
  }

  /** 明细：仅取本人这条（不存在或不归属 → empty，由 service 转 404）。 */
  public Optional<BrowseRunEntity> findByIdForUser(long userId, long id) {
    return jdbcTemplate
        .query(
            """
            SELECT id, user_id, goal, status, final_summary, truncated, error, created_at
            FROM browse_run
            WHERE id = ? AND user_id = ?
            """,
            this::map,
            id,
            userId)
        .stream()
        .findFirst();
  }

  private BrowseRunEntity map(ResultSet rs, int rowNum) throws SQLException {
    return new BrowseRunEntity(
        rs.getLong("id"),
        rs.getLong("user_id"),
        rs.getString("goal"),
        BrowseRunStatus.valueOf(rs.getString("status")),
        rs.getString("final_summary"),
        rs.getBoolean("truncated"),
        rs.getString("error"),
        offset(rs, "created_at"));
  }

  private OffsetDateTime offset(ResultSet rs, String column) throws SQLException {
    java.sql.Timestamp ts = rs.getTimestamp(column);
    return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
  }
}
