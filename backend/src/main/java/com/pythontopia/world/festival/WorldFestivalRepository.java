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
 * Reads the static {@code world_festival} catalog (seeded in V118) and flips the per-row
 * lifecycle {@code status} column when {@link WorldFestivalService} ticks. Status writes are
 * intentionally schedule-wide (no user_id) because {@code world_festival} is the canonical world
 * configuration table; per-user FESTIVAL_START / FESTIVAL_END emission is owned by
 * {@code WorldEventRepository} and will be wired in by the follow-up R-Live-Festival sub-task.
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
   * Return festivals whose {@code day_index} is strictly greater than {@code currentDay} and at
   * most {@code currentDay + withinDays}, ordered by ascending {@code day_index}. The current day
   * is excluded so the HUD can keep "now" and "upcoming" lanes separate; callers wanting today's
   * festival should use {@link #findByDay(int)} instead.
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
   * Flip the lifecycle {@code status} of the festival at {@code dayIndex}. No-ops silently when
   * no row matches (e.g. day without a festival); callers should pre-check via {@link
   * #findByDay(int)} if they need a hard "festival exists" assertion.
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
