package com.pythontopia.world.repository;

import com.pythontopia.world.dto.PlayerStateDto;
import com.pythontopia.world.dto.WorldSnapshotDto;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Reads the {@code game_bootstrap_view} created in V100/V107 to assemble the 1:1 world+player slice
 * in a single round-trip. Callers add the 1:N collections (crops, quests, mastery, inventory, …)
 * separately because the view intentionally only joins one-to-one relations.
 */
@Repository
public class GameBootstrapRepository {

  private final JdbcTemplate jdbcTemplate;

  public GameBootstrapRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<BootstrapRow> findRow(Long userId) {
    return jdbcTemplate
        .query(
            """
            SELECT world_session_id, day_index, weather, in_game_minutes,
                   month_index, weekday, season, time_of_day,
                   world_started_at, world_last_active_at,
                   player_state_id, player_avatar_key, player_map_key,
                   player_tile_x, player_tile_y, player_direction
            FROM game_bootstrap_view
            WHERE user_id = ?
            """,
            this::map,
            userId)
        .stream()
        .findFirst();
  }

  private BootstrapRow map(ResultSet rs, int rowNum) throws SQLException {
    Long sessionId = (Long) rs.getObject("world_session_id");
    WorldSnapshotDto world =
        sessionId == null
            ? null
            : new WorldSnapshotDto(
                sessionId,
                rs.getInt("day_index"),
                rs.getInt("month_index"),
                nonNull(rs.getString("weekday"), "monday"),
                nonNull(rs.getString("season"), "spring"),
                nonNull(rs.getString("time_of_day"), "morning"),
                nonNull(rs.getString("weather"), "SUNNY"),
                rs.getInt("in_game_minutes"),
                offset(rs, "world_started_at"),
                offset(rs, "world_last_active_at"));
    Long playerId = (Long) rs.getObject("player_state_id");
    PlayerStateDto player =
        playerId == null
            ? null
            : new PlayerStateDto(
                playerId,
                rs.getString("player_avatar_key"),
                rs.getString("player_map_key"),
                (Integer) rs.getObject("player_tile_x"),
                (Integer) rs.getObject("player_tile_y"),
                rs.getString("player_direction"));
    return new BootstrapRow(world, player);
  }

  private static String nonNull(String value, String fallback) {
    return value == null ? fallback : value;
  }

  private OffsetDateTime offset(ResultSet rs, String column) throws SQLException {
    java.sql.Timestamp ts = rs.getTimestamp(column);
    return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
  }

  public record BootstrapRow(WorldSnapshotDto world, PlayerStateDto player) {}
}
