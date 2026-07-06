package com.pythontopia.world.festival;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 读取静态 {@code world_festival} 目录（V118 seed），并在 {@link WorldFestivalService} tick 时翻转每行的
 * 生命周期 {@code status}。状态写入是全日程级（无 user_id），因为 {@code world_festival} 是世界配置的权威表；
 * 按用户的 FESTIVAL_START/FESTIVAL_END 发射由 {@code WorldEventRepository} 负责。
 */
@Repository
public class WorldFestivalRepository {

  private static final String SELECT_COLUMNS =
      "id, day_index, name_key, status, override_payload, created_at";

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public WorldFestivalRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public Optional<WorldFestival> findByDay(int dayIndex) {
    return jdbcTemplate
        .query(
            "SELECT " + SELECT_COLUMNS + " FROM world_festival WHERE day_index = ?",
            this::map,
            dayIndex)
        .stream()
        .findFirst();
  }

  public List<WorldFestival> findAll() {
    return jdbcTemplate.query(
        "SELECT " + SELECT_COLUMNS + " FROM world_festival ORDER BY day_index", this::map);
  }

  /**
   * 返回 {@code day_index} 严格大于 {@code currentDay} 且不超过 {@code currentDay + withinDays} 的节日，按
   * {@code day_index} 升序。排除当天，使 HUD 能区分「当前」与「即将」；要查当天用 {@link #findByDay(int)}。
   */
  public List<WorldFestival> findUpcoming(int currentDay, int withinDays) {
    return jdbcTemplate.query(
        "SELECT " + SELECT_COLUMNS
            + " FROM world_festival WHERE day_index > ? AND day_index <= ? ORDER BY day_index",
        this::map,
        currentDay,
        currentDay + withinDays);
  }

  /**
   * 翻转 {@code dayIndex} 处节日的生命周期 {@code status}。无匹配行时静默 no-op（如无节日的日子）；
   * 需要硬断言「节日存在」的调用方应先用 {@link #findByDay(int)} 预检。
   */
  public void updateStatus(int dayIndex, String status) {
    jdbcTemplate.update(
        "UPDATE world_festival SET status = ? WHERE day_index = ?", status, dayIndex);
  }

  private WorldFestival map(ResultSet rs, int rowNum) throws SQLException {
    return new WorldFestival(
        rs.getLong("id"),
        rs.getInt("day_index"),
        rs.getString("name_key"),
        rs.getString("status"),
        readJson(rs.getString("override_payload")),
        offset(rs, "created_at"));
  }

  private JsonNode readJson(String raw) {
    if (raw == null) {
      return null;
    }
    try {
      return objectMapper.readTree(raw);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(
          "Failed to parse world_festival.override_payload JSON", exception);
    }
  }

  private OffsetDateTime offset(ResultSet rs, String column) throws SQLException {
    java.sql.Timestamp ts = rs.getTimestamp(column);
    return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
  }
}
