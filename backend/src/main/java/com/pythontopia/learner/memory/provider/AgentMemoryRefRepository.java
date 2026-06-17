package com.pythontopia.learner.memory.provider;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** {@code agent_memory_ref}（V169）的读写：外部记忆 id 映射与 remember 审计的唯一持久化入口。 */
@Repository
public class AgentMemoryRefRepository {

  private final JdbcTemplate jdbcTemplate;

  public AgentMemoryRefRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * 记录一条外部记忆映射；按 {@code (provider, external_id)} 幂等（重复 remember 不重复落行）。
   *
   * @return 是否实际新增（false = 命中唯一键被忽略）
   */
  public boolean record(long userId, String provider, String externalId, String kind) {
    int affected =
        jdbcTemplate.update(
            """
            INSERT INTO agent_memory_ref (user_id, provider, external_id, kind)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (provider, external_id) DO NOTHING
            """,
            userId,
            provider,
            externalId,
            kind == null || kind.isBlank() ? "fact" : kind);
    return affected > 0;
  }

  /** 取该学生最近的记忆映射（审计 / Phase 2 对账用）。 */
  public List<AgentMemoryRef> findByUser(long userId, int limit) {
    return jdbcTemplate.query(
        """
        SELECT id, user_id, provider, external_id, kind, created_at
        FROM agent_memory_ref
        WHERE user_id = ?
        ORDER BY created_at DESC
        LIMIT ?
        """,
        AgentMemoryRefRepository::map,
        userId,
        Math.max(1, limit));
  }

  private static AgentMemoryRef map(ResultSet rs, int rowNum) throws SQLException {
    Timestamp createdAt = rs.getTimestamp("created_at");
    return new AgentMemoryRef(
        rs.getLong("id"),
        rs.getLong("user_id"),
        rs.getString("provider"),
        rs.getString("external_id"),
        rs.getString("kind"),
        createdAt == null ? Instant.EPOCH : createdAt.toInstant());
  }
}
