package com.pythontopia.world.npc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read + write helpers for the per-learner NPC dialog state and gift log.
 *
 * <p>All mutations clamp friendship into the {@code [0, 100]} range to honour the V101 CHECK
 * constraint. Flags are merged at the top-level keys (later writes win).
 */
@Repository
public class NpcDialogStateRepository {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public NpcDialogStateRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public Optional<NpcDialogState> findOne(Long userId, String npcKey) {
    return jdbcTemplate
        .query(
            """
            SELECT id, user_id, npc_key, current_node_id, completed_node_ids,
                   friendship, last_talked_at, flags, created_at, updated_at
            FROM npc_dialog_state
            WHERE user_id = ? AND npc_key = ?
            """,
            this::map,
            userId,
            npcKey)
        .stream()
        .findFirst();
  }

  public List<NpcDialogState> findAll(Long userId) {
    return jdbcTemplate.query(
        """
        SELECT id, user_id, npc_key, current_node_id, completed_node_ids,
               friendship, last_talked_at, flags, created_at, updated_at
        FROM npc_dialog_state
        WHERE user_id = ?
        ORDER BY npc_key
        """,
        this::map,
        userId);
  }

  /**
   * Lazily initialise a dialog state row so the caller never has to deal with a missing record.
   * Returns the existing row when one is already present.
   */
  public NpcDialogState findOrInit(Long userId, String npcKey) {
    Optional<NpcDialogState> existing = findOne(userId, npcKey);
    if (existing.isPresent()) {
      return existing.get();
    }
    jdbcTemplate.update(
        """
        INSERT INTO npc_dialog_state (user_id, npc_key)
        VALUES (?, ?)
        ON CONFLICT (user_id, npc_key) DO NOTHING
        """,
        userId,
        npcKey);
    return findOne(userId, npcKey).orElseThrow();
  }

  /**
   * Upsert the dialog node progress for a specific NPC. {@code completedNodeIds} replaces the
   * stored array; the caller is responsible for merging.
   */
  public NpcDialogState upsertNodeProgress(
      Long userId, String npcKey, String currentNodeId, List<String> completedNodeIds) {
    String[] nodes =
        completedNodeIds == null
            ? new String[0]
            : completedNodeIds.stream().filter(s -> s != null && !s.isBlank()).toArray(String[]::new);
    jdbcTemplate.update(
        """
        INSERT INTO npc_dialog_state (user_id, npc_key, current_node_id, completed_node_ids,
                                      last_talked_at)
        VALUES (?, ?, ?, ?, now())
        ON CONFLICT (user_id, npc_key) DO UPDATE
        SET current_node_id = EXCLUDED.current_node_id,
            completed_node_ids = EXCLUDED.completed_node_ids,
            last_talked_at = now(),
            updated_at = now()
        """,
        userId,
        npcKey,
        currentNodeId,
        nodes);
    return findOne(userId, npcKey).orElseThrow();
  }

  /**
   * Adjust friendship by {@code delta}, clamping the persisted value into {@code [0,100]}. Returns
   * the new friendship value. Lazily inserts the row when missing.
   */
  public int incrementFriendship(Long userId, String npcKey, int delta) {
    findOrInit(userId, npcKey);
    Integer current =
        jdbcTemplate.queryForObject(
            """
            UPDATE npc_dialog_state
            SET friendship = LEAST(100, GREATEST(0, friendship + ?)),
                updated_at = now()
            WHERE user_id = ? AND npc_key = ?
            RETURNING friendship
            """,
            Integer.class,
            delta,
            userId,
            npcKey);
    return current == null ? 0 : current;
  }

  /** Shallow-merge JSON keys into the existing {@code flags} object. Existing keys are replaced. */
  public NpcDialogState mergeFlags(Long userId, String npcKey, JsonNode patch) {
    if (patch == null || !patch.isObject()) {
      return findOrInit(userId, npcKey);
    }
    NpcDialogState state = findOrInit(userId, npcKey);
    ObjectNode flags;
    if (state.flags() instanceof ObjectNode existing) {
      flags = existing.deepCopy();
    } else {
      flags = objectMapper.createObjectNode();
    }
    patch.fields().forEachRemaining(entry -> flags.set(entry.getKey(), entry.getValue()));
    jdbcTemplate.update(
        """
        UPDATE npc_dialog_state
        SET flags = ?::jsonb,
            updated_at = now()
        WHERE user_id = ? AND npc_key = ?
        """,
        writeJson(flags),
        userId,
        npcKey);
    return findOne(userId, npcKey).orElseThrow();
  }

  public NpcGiftLog logGift(
      Long userId, String npcKey, String itemKey, String giftTier, int friendshipDelta) {
    Long id =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO npc_gift_log (user_id, npc_key, item_key, gift_tier, friendship_delta)
            VALUES (?, ?, ?, ?, ?)
            RETURNING id
            """,
            Long.class,
            userId,
            npcKey,
            itemKey,
            giftTier,
            friendshipDelta);
    return jdbcTemplate
        .query(
            """
            SELECT id, user_id, npc_key, item_key, gift_tier, friendship_delta, given_at
            FROM npc_gift_log
            WHERE id = ?
            """,
            this::mapGift,
            id)
        .stream()
        .findFirst()
        .orElseThrow();
  }

  public List<NpcGiftLog> recentGifts(Long userId, String npcKey, int limit) {
    return jdbcTemplate.query(
        """
        SELECT id, user_id, npc_key, item_key, gift_tier, friendship_delta, given_at
        FROM npc_gift_log
        WHERE user_id = ? AND npc_key = ?
        ORDER BY given_at DESC
        LIMIT ?
        """,
        this::mapGift,
        userId,
        npcKey,
        Math.max(1, Math.min(100, limit)));
  }

  private NpcDialogState map(ResultSet rs, int rowNum) throws SQLException {
    return new NpcDialogState(
        rs.getLong("id"),
        rs.getLong("user_id"),
        rs.getString("npc_key"),
        rs.getString("current_node_id"),
        readStringArray(rs.getArray("completed_node_ids")),
        rs.getInt("friendship"),
        offset(rs, "last_talked_at"),
        readJson(rs.getString("flags")),
        offset(rs, "created_at"),
        offset(rs, "updated_at"));
  }

  private NpcGiftLog mapGift(ResultSet rs, int rowNum) throws SQLException {
    return new NpcGiftLog(
        rs.getLong("id"),
        rs.getLong("user_id"),
        rs.getString("npc_key"),
        rs.getString("item_key"),
        rs.getString("gift_tier"),
        rs.getInt("friendship_delta"),
        offset(rs, "given_at"));
  }

  private List<String> readStringArray(Array array) throws SQLException {
    if (array == null) {
      return List.of();
    }
    Object raw = array.getArray();
    if (raw instanceof Object[] values) {
      return Arrays.stream(values).map(String::valueOf).toList();
    }
    return List.of();
  }

  private JsonNode readJson(String raw) {
    try {
      return raw == null ? objectMapper.createObjectNode() : objectMapper.readTree(raw);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to parse npc_dialog_state.flags JSON", exception);
    }
  }

  private String writeJson(JsonNode value) {
    try {
      return objectMapper.writeValueAsString(value == null ? objectMapper.createObjectNode() : value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to serialise npc_dialog_state.flags JSON", exception);
    }
  }

  private OffsetDateTime offset(ResultSet rs, String column) throws SQLException {
    java.sql.Timestamp ts = rs.getTimestamp(column);
    return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
  }
}
