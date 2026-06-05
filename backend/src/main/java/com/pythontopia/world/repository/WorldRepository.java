package com.pythontopia.world.repository;

import com.pythontopia.world.domain.PlayerState;
import com.pythontopia.world.domain.WorldSession;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class WorldRepository {

  private final JdbcTemplate jdbcTemplate;

  public WorldRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<WorldSession> findSession(Long userId) {
    return jdbcTemplate
        .query(
            """
            SELECT id, user_id, day_index, weather, in_game_minutes
            FROM world_session
            WHERE user_id = ?
            """,
            this::mapSession,
            userId)
        .stream()
        .findFirst();
  }

  public WorldSession createSession(Long userId) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(
        connection -> {
          var statement =
              connection.prepareStatement(
                  """
                  INSERT INTO world_session (user_id, day_index, weather, in_game_minutes)
                  VALUES (?, 1, 'SUNNY', 480)
                  """,
                  new String[] {"id"});
          statement.setLong(1, userId);
          return statement;
        },
        keyHolder);
    return findSession(userId).orElseThrow();
  }

  /**
   * 将世界推进一个游戏内日（day_index +1），写入服务层算好的天气/星期/季节/月份/时段；返回 tick 后的 session。
   */
  public WorldSession advanceDay(
      Long userId,
      String newWeather,
      String newWeekday,
      String newSeason,
      int newMonthIndex,
      String newTimeOfDay) {
    jdbcTemplate.update(
        """
        UPDATE world_session
        SET day_index = day_index + 1,
            weather = ?,
            weekday = ?,
            season = ?,
            month_index = ?,
            time_of_day = ?,
            in_game_minutes = 480,
            last_active_at = now()
        WHERE user_id = ?
        """,
        newWeather,
        newWeekday,
        newSeason,
        newMonthIndex,
        newTimeOfDay,
        userId);
    return findSession(userId).orElseThrow();
  }

  public Optional<PlayerState> findPlayer(Long userId) {
    return jdbcTemplate
        .query(
            """
            SELECT id, user_id, avatar_key, map_key, tile_x, tile_y, direction
            FROM player_state
            WHERE user_id = ?
            """,
            this::mapPlayer,
            userId)
        .stream()
        .findFirst();
  }

  public PlayerState createPlayer(Long userId) {
    jdbcTemplate.update("INSERT INTO player_state (user_id) VALUES (?)", userId);
    return findPlayer(userId).orElseThrow();
  }

  public PlayerState updatePlayer(Long userId, Integer tileX, Integer tileY, String direction) {
    jdbcTemplate.update(
        """
        INSERT INTO player_state (user_id, tile_x, tile_y, direction)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (user_id) DO UPDATE
        SET tile_x = EXCLUDED.tile_x, tile_y = EXCLUDED.tile_y,
            direction = EXCLUDED.direction, updated_at = now()
        """,
        userId,
        tileX,
        tileY,
        direction);
    return findPlayer(userId).orElseThrow();
  }

  private WorldSession mapSession(ResultSet rs, int rowNum) throws SQLException {
    return new WorldSession(
        rs.getLong("id"),
        rs.getLong("user_id"),
        rs.getInt("day_index"),
        rs.getString("weather"),
        rs.getInt("in_game_minutes"));
  }

  private PlayerState mapPlayer(ResultSet rs, int rowNum) throws SQLException {
    return new PlayerState(
        rs.getLong("id"),
        rs.getLong("user_id"),
        rs.getString("avatar_key"),
        rs.getString("map_key"),
        rs.getInt("tile_x"),
        rs.getInt("tile_y"),
        rs.getString("direction"));
  }
}
