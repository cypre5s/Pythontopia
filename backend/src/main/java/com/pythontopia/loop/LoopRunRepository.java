package com.pythontopia.loop;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * {@code loop_run}（V171）读写辅助。用 {@link JdbcTemplate}（项目持久化约定，非 JPA），{@code tools_used}
 * 走 {@code text[]}。插入用 {@code INSERT ... RETURNING} 单次往返拿回含 DB 生成 {@code id}/{@code created_at}
 * 的完整行。归一/校验由 {@link LoopRunService} 完成，本层只做忠实落库。
 */
@Repository
public class LoopRunRepository {

  private static final String SELECT_COLUMNS =
      "id, user_id, session_id, turn, max_steps, steps_used, replan_count, tools_used,"
          + " grounding_pass, safety_pass, input_tokens, output_tokens, trace_id, created_at";

  private final JdbcTemplate jdbcTemplate;

  public LoopRunRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /** 已归一/校验的落库载荷，由 {@link LoopRunService} 构造。 */
  public record LoopRunInsert(
      long userId,
      String sessionId,
      int turn,
      int maxSteps,
      int stepsUsed,
      int replanCount,
      List<String> toolsUsed,
      boolean groundingPass,
      boolean safetyPass,
      int inputTokens,
      int outputTokens,
      String traceId) {}

  public LoopRunEntity insert(LoopRunInsert insert) {
    String[] tools = insert.toolsUsed().toArray(new String[0]);
    return jdbcTemplate.query(
        connection -> {
          PreparedStatement ps =
              connection.prepareStatement(
                  "INSERT INTO loop_run (user_id, session_id, turn, max_steps, steps_used,"
                      + " replan_count, tools_used, grounding_pass, safety_pass, input_tokens,"
                      + " output_tokens, trace_id)"
                      + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                      + " RETURNING " + SELECT_COLUMNS);
          ps.setLong(1, insert.userId());
          ps.setString(2, insert.sessionId());
          ps.setInt(3, insert.turn());
          ps.setInt(4, insert.maxSteps());
          ps.setInt(5, insert.stepsUsed());
          ps.setInt(6, insert.replanCount());
          Array toolsArray = connection.createArrayOf("text", tools);
          ps.setArray(7, toolsArray);
          ps.setBoolean(8, insert.groundingPass());
          ps.setBoolean(9, insert.safetyPass());
          ps.setInt(10, insert.inputTokens());
          ps.setInt(11, insert.outputTokens());
          ps.setString(12, insert.traceId());
          return ps;
        },
        rs -> {
          if (!rs.next()) {
            throw new IllegalStateException("loop_run insert returned no row");
          }
          return map(rs, 1);
        });
  }

  public Optional<LoopRunEntity> findById(Long id) {
    return jdbcTemplate
        .query("SELECT " + SELECT_COLUMNS + " FROM loop_run WHERE id = ?", this::map, id)
        .stream()
        .findFirst();
  }

  public int deleteCreatedBefore(OffsetDateTime cutoff) {
    return jdbcTemplate.update("DELETE FROM loop_run WHERE created_at < ?", cutoff);
  }

  private LoopRunEntity map(ResultSet rs, int rowNum) throws SQLException {
    return new LoopRunEntity(
        rs.getLong("id"),
        rs.getLong("user_id"),
        rs.getString("session_id"),
        rs.getInt("turn"),
        rs.getInt("max_steps"),
        rs.getInt("steps_used"),
        rs.getInt("replan_count"),
        readStringArray(rs.getArray("tools_used")),
        rs.getBoolean("grounding_pass"),
        rs.getBoolean("safety_pass"),
        rs.getInt("input_tokens"),
        rs.getInt("output_tokens"),
        rs.getString("trace_id"),
        offset(rs, "created_at"));
  }

  private List<String> readStringArray(Array array) throws SQLException {
    if (array == null) {
      return List.of();
    }
    Object raw = array.getArray();
    if (raw instanceof String[] values) {
      return Arrays.asList(values);
    }
    if (raw instanceof Object[] values) {
      return Arrays.stream(values).filter(Objects::nonNull).map(Object::toString).toList();
    }
    return List.of();
  }

  private OffsetDateTime offset(ResultSet rs, String column) throws SQLException {
    java.sql.Timestamp ts = rs.getTimestamp(column);
    return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
  }
}
