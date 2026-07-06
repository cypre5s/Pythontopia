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
 * 按学习者维度的 NPC 对话状态读写助手。
 *
 * <p>flags 按顶层 key 合并（后写覆盖）。
 */
@Repository
public class NpcDialogStateRepository {

  private static final List<String> TOWN_HUB_NPC_KEYS =
      List.of(
          "mayor_py",
          "foreman_bug",
          "librarian_doc",
          "coach_q",
          "sage_review",
          "professor",
          "mine_keeper",
          "student_a",
          "class_president",
          "workshop_master",
          "arena_guard");

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
                   last_talked_at, flags, created_at, updated_at
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
               last_talked_at, flags, created_at, updated_at
        FROM npc_dialog_state
        WHERE user_id = ?
        ORDER BY npc_key
        """,
        this::map,
        userId);
  }

  public List<NpcDialogState> findAllOrInitTownHub(Long userId) {
    for (String npcKey : TOWN_HUB_NPC_KEYS) {
      jdbcTemplate.update(
          """
          INSERT INTO npc_dialog_state (user_id, npc_key)
          VALUES (?, ?)
          ON CONFLICT (user_id, npc_key) DO NOTHING
          """,
          userId,
          npcKey);
    }
    return findAll(userId);
  }

  /** 惰性初始化对话状态行，使调用方无需处理记录缺失；已存在则直接返回现有行。 */
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

  /** upsert 指定 NPC 的对话节点进度；{@code completedNodeIds} 整体替换已存数组，合并由调用方负责。 */
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

  /** 将 JSON key 浅合并进现有 {@code flags} 对象；同名 key 被覆盖。 */
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

  private NpcDialogState map(ResultSet rs, int rowNum) throws SQLException {
    return new NpcDialogState(
        rs.getLong("id"),
        rs.getLong("user_id"),
        rs.getString("npc_key"),
        rs.getString("current_node_id"),
        readStringArray(rs.getArray("completed_node_ids")),
        offset(rs, "last_talked_at"),
        readJson(rs.getString("flags")),
        offset(rs, "created_at"),
        offset(rs, "updated_at"));
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
