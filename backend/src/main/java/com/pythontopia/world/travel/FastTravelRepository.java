package com.pythontopia.world.travel;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JDBC-backed read access for the {@code fast_travel_route} static table. The table is treated as
 * write-only by Flyway seeds; mutations happen exclusively through migrations to keep the route
 * graph reproducible across environments.
 */
@Repository
public class FastTravelRepository {

  private static final RowMapper<FastTravelRoute> ROW_MAPPER =
      (ResultSet rs, int rowNum) -> mapRow(rs);

  private final JdbcTemplate jdbcTemplate;

  public FastTravelRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<FastTravelRoute> find(String fromScene, String toScene) {
    return jdbcTemplate
        .query(
            """
            SELECT id, from_scene, to_scene, unlock_phase,
                   cost_pythoco, cost_energy, cost_minutes
            FROM fast_travel_route
            WHERE from_scene = ? AND to_scene = ?
            """,
            ROW_MAPPER,
            fromScene,
            toScene)
        .stream()
        .findFirst();
  }

  public List<FastTravelRoute> findUnlockedFromScene(String fromScene, int currentPhase) {
    return jdbcTemplate.query(
        """
        SELECT id, from_scene, to_scene, unlock_phase,
               cost_pythoco, cost_energy, cost_minutes
        FROM fast_travel_route
        WHERE from_scene = ? AND unlock_phase <= ?
        ORDER BY unlock_phase, to_scene
        """,
        ROW_MAPPER,
        fromScene,
        currentPhase);
  }

  public List<FastTravelRoute> findAll() {
    return jdbcTemplate.query(
        """
        SELECT id, from_scene, to_scene, unlock_phase,
               cost_pythoco, cost_energy, cost_minutes
        FROM fast_travel_route
        ORDER BY from_scene, to_scene
        """,
        ROW_MAPPER);
  }

  private static FastTravelRoute mapRow(ResultSet rs) throws SQLException {
    return new FastTravelRoute(
        rs.getLong("id"),
        rs.getString("from_scene"),
        rs.getString("to_scene"),
        rs.getInt("unlock_phase"),
        rs.getInt("cost_pythoco"),
        rs.getInt("cost_energy"),
        rs.getInt("cost_minutes"));
  }
}
