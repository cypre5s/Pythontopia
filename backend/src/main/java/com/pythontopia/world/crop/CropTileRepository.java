package com.pythontopia.world.crop;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CropTileRepository {

  private final JdbcTemplate jdbcTemplate;

  public CropTileRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<CropTile> findByUser(Long userId, int packId) {
    return jdbcTemplate.query(
        """
        SELECT id, user_id, tile_x, tile_y, kc_code, growth_stage, mastery_score,
               status, unlocked, unlocked_at, updated_at, pack_id
        FROM crop_tile_state
        WHERE user_id = ? AND pack_id = ?
        ORDER BY tile_y, tile_x
        """,
        this::map,
        userId,
        packId);
  }

  public Optional<CropTile> findOne(Long userId, int packId, String kcCode) {
    return jdbcTemplate
        .query(
            """
            SELECT id, user_id, tile_x, tile_y, kc_code, growth_stage, mastery_score,
                   status, unlocked, unlocked_at, updated_at, pack_id
            FROM crop_tile_state
            WHERE user_id = ? AND pack_id = ? AND kc_code = ?
            """,
            this::map,
            userId,
            packId,
            kcCode)
        .stream()
        .findFirst();
  }

  public void insertIfMissing(
      Long userId,
      int packId,
      String kcCode,
      int tileX,
      int tileY,
      boolean unlocked,
      CropStatus status) {
    jdbcTemplate.update(
        """
        INSERT INTO crop_tile_state (
          user_id, tile_x, tile_y, kc_code, growth_stage, mastery_score,
          status, unlocked, unlocked_at, pack_id
        )
        VALUES (?, ?, ?, ?, 0, 0.0, ?, ?, CASE WHEN ? THEN now() ELSE NULL END, ?)
        ON CONFLICT (user_id, pack_id, kc_code) DO NOTHING
        """,
        userId,
        tileX,
        tileY,
        kcCode,
        status.name(),
        unlocked,
        unlocked,
        packId);
  }

  public void unlock(Long userId, int packId, String kcCode, int tileX, int tileY) {
    jdbcTemplate.update(
        """
        UPDATE crop_tile_state
        SET unlocked = TRUE,
            unlocked_at = COALESCE(unlocked_at, now()),
            status = CASE WHEN status = 'LOCKED' THEN 'SEED' ELSE status END,
            tile_x = ?,
            tile_y = ?,
            updated_at = now()
        WHERE user_id = ? AND pack_id = ? AND kc_code = ?
        """,
        tileX,
        tileY,
        userId,
        packId,
        kcCode);
  }

  public void updateMasteryAndStatus(
      Long userId, int packId, String kcCode, BigDecimal masteryScore, CropStatus status, int growthStage) {
    jdbcTemplate.update(
        """
        UPDATE crop_tile_state
        SET mastery_score = ?, status = ?, growth_stage = ?, updated_at = now()
        WHERE user_id = ? AND pack_id = ? AND kc_code = ?
        """,
        masteryScore,
        status.name(),
        growthStage,
        userId,
        packId,
        kcCode);
  }

  public int countByUser(Long userId, int packId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM crop_tile_state WHERE user_id = ? AND pack_id = ?",
            Integer.class,
            userId,
            packId);
    return count == null ? 0 : count;
  }

  public int countUnlocked(Long userId, int packId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM crop_tile_state WHERE user_id = ? AND pack_id = ? AND unlocked = TRUE",
            Integer.class,
            userId,
            packId);
    return count == null ? 0 : count;
  }

  private CropTile map(ResultSet rs, int rowNum) throws SQLException {
    return new CropTile(
        rs.getLong("id"),
        rs.getLong("user_id"),
        rs.getInt("tile_x"),
        rs.getInt("tile_y"),
        rs.getString("kc_code"),
        rs.getInt("growth_stage"),
        rs.getBigDecimal("mastery_score"),
        CropStatus.valueOf(rs.getString("status")),
        rs.getBoolean("unlocked"),
        offset(rs, "unlocked_at"),
        offset(rs, "updated_at"),
        rs.getInt("pack_id"));
  }

  private OffsetDateTime offset(ResultSet rs, String column) throws SQLException {
    java.sql.Timestamp ts = rs.getTimestamp(column);
    return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
  }
}
