package com.pythontopia.world.save;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.error.BusinessException;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Save slot read + write helpers. Honours the V108 partial unique index ({@code is_active=true}
 * exists for at most one slot per user) by always deactivating siblings inside a transaction
 * before activating a target slot.
 *
 * <p>V114 introduced four extension columns (scene_name, unlocked_buildings,
 * accumulated_play_seconds, inventory_items) for the W3 RPG save state. All read paths include
 * them; writes accept defaults from the DB unless callers go through {@link #updateExtension}.
 */
@Repository
public class SaveSlotRepository {

  private static final int MAX_SLOT_INDEX = 5;
  private static final String SELECT_COLUMNS =
      "id, user_id, slot_index, display_name, is_active, created_at, last_played_at, snapshot, "
          + "scene_name, unlocked_buildings, accumulated_play_seconds, inventory_items";

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public SaveSlotRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public List<SaveSlot> findAll(Long userId) {
    return jdbcTemplate.query(
        "SELECT " + SELECT_COLUMNS + " FROM save_slot WHERE user_id = ? ORDER BY slot_index",
        this::map,
        userId);
  }

  public Optional<SaveSlot> findActive(Long userId) {
    return jdbcTemplate
        .query(
            "SELECT " + SELECT_COLUMNS + " FROM save_slot WHERE user_id = ? AND is_active = true",
            this::map,
            userId)
        .stream()
        .findFirst();
  }

  public Optional<SaveSlot> findOne(Long userId, Integer slotIndex) {
    return jdbcTemplate
        .query(
            "SELECT " + SELECT_COLUMNS + " FROM save_slot WHERE user_id = ? AND slot_index = ?",
            this::map,
            userId,
            slotIndex)
        .stream()
        .findFirst();
  }

  /**
   * Insert a fresh slot at {@code slotIndex}. Marked active only when the learner has no other
   * slots, otherwise the caller must explicitly {@link #activateSlot}.
   *
   * <p>The V114 extension columns (scene_name / unlocked_buildings / accumulated_play_seconds /
   * inventory_items) intentionally fall back to DB defaults here. Callers that need to seed them
   * upfront should call {@link #updateExtension} right after this.
   */
  public SaveSlot createSlot(Long userId, int slotIndex, String displayName) {
    requireValidIndex(slotIndex);
    if (displayName == null || displayName.isBlank()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "SAVE_SLOT_NAME_REQUIRED", "Save slot displayName is required");
    }
    boolean shouldActivate = findAll(userId).isEmpty();
    jdbcTemplate.update(
        """
        INSERT INTO save_slot (user_id, slot_index, display_name, is_active)
        VALUES (?, ?, ?, ?)
        """,
        userId,
        slotIndex,
        displayName,
        shouldActivate);
    return findOne(userId, slotIndex).orElseThrow();
  }

  /**
   * Deactivate every slot of the user, then activate the target slot atomically. Throws when the
   * target slot does not exist.
   */
  @Transactional
  public SaveSlot activateSlot(Long userId, int slotIndex) {
    requireValidIndex(slotIndex);
    if (findOne(userId, slotIndex).isEmpty()) {
      throw new BusinessException(
          HttpStatus.NOT_FOUND, "SAVE_SLOT_NOT_FOUND", "Save slot " + slotIndex + " not found");
    }
    jdbcTemplate.update(
        "UPDATE save_slot SET is_active = false WHERE user_id = ? AND is_active = true", userId);
    jdbcTemplate.update(
        """
        UPDATE save_slot
        SET is_active = true, last_played_at = now()
        WHERE user_id = ? AND slot_index = ?
        """,
        userId,
        slotIndex);
    return findOne(userId, slotIndex).orElseThrow();
  }

  /** Replace the snapshot blob for an existing slot and bump {@code last_played_at}. */
  public SaveSlot updateSnapshot(Long userId, int slotIndex, String snapshotJson) {
    requireValidIndex(slotIndex);
    if (findOne(userId, slotIndex).isEmpty()) {
      throw new BusinessException(
          HttpStatus.NOT_FOUND, "SAVE_SLOT_NOT_FOUND", "Save slot " + slotIndex + " not found");
    }
    String payload = snapshotJson == null || snapshotJson.isBlank() ? "{}" : snapshotJson;
    jdbcTemplate.update(
        """
        UPDATE save_slot
        SET snapshot = ?::jsonb, last_played_at = now()
        WHERE user_id = ? AND slot_index = ?
        """,
        payload,
        userId,
        slotIndex);
    return findOne(userId, slotIndex).orElseThrow();
  }

  /**
   * Update only the V114 extension columns of an existing slot. Any {@code null} argument leaves
   * the corresponding column untouched (COALESCE) so callers can patch a subset at a time.
   */
  public SaveSlot updateExtension(
      Long userId,
      int slotIndex,
      String sceneName,
      List<String> unlockedBuildings,
      Long accumulatedPlaySeconds,
      String inventoryItemsJson) {
    requireValidIndex(slotIndex);
    if (findOne(userId, slotIndex).isEmpty()) {
      throw new BusinessException(
          HttpStatus.NOT_FOUND, "SAVE_SLOT_NOT_FOUND", "Save slot " + slotIndex + " not found");
    }
    jdbcTemplate.update(
        connection -> {
          var ps =
              connection.prepareStatement(
                  """
                  UPDATE save_slot
                  SET scene_name = COALESCE(?, scene_name),
                      unlocked_buildings = COALESCE(?, unlocked_buildings),
                      accumulated_play_seconds = COALESCE(?, accumulated_play_seconds),
                      inventory_items = COALESCE(?::jsonb, inventory_items),
                      last_played_at = now()
                  WHERE user_id = ? AND slot_index = ?
                  """);
          if (sceneName == null) {
            ps.setNull(1, java.sql.Types.VARCHAR);
          } else {
            ps.setString(1, sceneName);
          }
          if (unlockedBuildings == null) {
            ps.setNull(2, java.sql.Types.ARRAY);
          } else {
            Array array =
                connection.createArrayOf("TEXT", unlockedBuildings.toArray(new String[0]));
            ps.setArray(2, array);
          }
          if (accumulatedPlaySeconds == null) {
            ps.setNull(3, java.sql.Types.BIGINT);
          } else {
            ps.setLong(3, accumulatedPlaySeconds);
          }
          if (inventoryItemsJson == null || inventoryItemsJson.isBlank()) {
            ps.setNull(4, java.sql.Types.VARCHAR);
          } else {
            ps.setString(4, inventoryItemsJson);
          }
          ps.setLong(5, userId);
          ps.setInt(6, slotIndex);
          return ps;
        });
    return findOne(userId, slotIndex).orElseThrow();
  }

  private void requireValidIndex(int slotIndex) {
    if (slotIndex < 0 || slotIndex > MAX_SLOT_INDEX) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "SAVE_SLOT_INDEX_INVALID",
          "slotIndex must be between 0 and " + MAX_SLOT_INDEX);
    }
  }

  private SaveSlot map(ResultSet rs, int rowNum) throws SQLException {
    return new SaveSlot(
        rs.getLong("id"),
        rs.getLong("user_id"),
        rs.getInt("slot_index"),
        rs.getString("display_name"),
        rs.getBoolean("is_active"),
        offset(rs, "created_at"),
        offset(rs, "last_played_at"),
        readJson(rs.getString("snapshot")),
        rs.getString("scene_name"),
        readStringArray(rs.getArray("unlocked_buildings")),
        rs.getLong("accumulated_play_seconds"),
        readJson(rs.getString("inventory_items")));
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
      return Arrays.stream(values)
          .filter(java.util.Objects::nonNull)
          .map(Object::toString)
          .toList();
    }
    return List.of();
  }

  private JsonNode readJson(String raw) {
    try {
      return raw == null ? objectMapper.createObjectNode() : objectMapper.readTree(raw);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to parse save_slot JSON column", exception);
    }
  }

  private OffsetDateTime offset(ResultSet rs, String column) throws SQLException {
    java.sql.Timestamp ts = rs.getTimestamp(column);
    return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
  }
}
