package com.pythontopia.world.skill;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Skill tree static catalog + per-learner unlocked skill records. The unlock condition itself is
 * stored as JSONB and evaluated by {@code SkillService} against {@code kc_mastery_snapshot}.
 */
@Repository
public class SkillRepository {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public SkillRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public List<SkillNode> findAllNodes() {
    return jdbcTemplate.query(
        """
        SELECT skill_key, display_name, description, kc_codes, unlock_condition, icon_url,
               tier, create_time
        FROM skill_node
        ORDER BY tier, skill_key
        """,
        this::mapNode);
  }

  public Optional<SkillNode> findNode(String skillKey) {
    return jdbcTemplate
        .query(
            """
            SELECT skill_key, display_name, description, kc_codes, unlock_condition, icon_url,
                   tier, create_time
            FROM skill_node
            WHERE skill_key = ?
            """,
            this::mapNode,
            skillKey)
        .stream()
        .findFirst();
  }

  public List<LearnerSkill> findUnlocked(Long userId) {
    return jdbcTemplate.query(
        """
        SELECT id, user_id, skill_key, unlocked_at, proficiency
        FROM learner_skill
        WHERE user_id = ?
        ORDER BY unlocked_at DESC
        """,
        this::mapLearnerSkill,
        userId);
  }

  public Optional<LearnerSkill> findLearnerSkill(Long userId, String skillKey) {
    return jdbcTemplate
        .query(
            """
            SELECT id, user_id, skill_key, unlocked_at, proficiency
            FROM learner_skill
            WHERE user_id = ? AND skill_key = ?
            """,
            this::mapLearnerSkill,
            userId,
            skillKey)
        .stream()
        .findFirst();
  }

  /** Idempotent insert — returns the row whether it was already present or just created. */
  public LearnerSkill unlockSkill(Long userId, String skillKey) {
    jdbcTemplate.update(
        """
        INSERT INTO learner_skill (user_id, skill_key)
        VALUES (?, ?)
        ON CONFLICT (user_id, skill_key) DO NOTHING
        """,
        userId,
        skillKey);
    return findLearnerSkill(userId, skillKey).orElseThrow();
  }

  /**
   * Adjust the proficiency of an already-unlocked skill, clamping into {@code [0, 100]}. Returns
   * the new proficiency. Throws when the learner has not unlocked the skill yet.
   */
  public int updateProficiency(Long userId, String skillKey, int delta) {
    Integer updated =
        jdbcTemplate.queryForObject(
            """
            UPDATE learner_skill
            SET proficiency = LEAST(100, GREATEST(0, proficiency + ?))
            WHERE user_id = ? AND skill_key = ?
            RETURNING proficiency
            """,
            Integer.class,
            delta,
            userId,
            skillKey);
    return updated == null ? 0 : updated;
  }

  private SkillNode mapNode(ResultSet rs, int rowNum) throws SQLException {
    return new SkillNode(
        rs.getString("skill_key"),
        rs.getString("display_name"),
        rs.getString("description"),
        readStringArray(rs.getArray("kc_codes")),
        readJson(rs.getString("unlock_condition")),
        rs.getString("icon_url"),
        rs.getInt("tier"),
        offset(rs, "create_time"));
  }

  private LearnerSkill mapLearnerSkill(ResultSet rs, int rowNum) throws SQLException {
    return new LearnerSkill(
        rs.getLong("id"),
        rs.getLong("user_id"),
        rs.getString("skill_key"),
        offset(rs, "unlocked_at"),
        rs.getInt("proficiency"));
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
      throw new IllegalStateException("Failed to parse skill_node.unlock_condition JSON", exception);
    }
  }

  private OffsetDateTime offset(ResultSet rs, String column) throws SQLException {
    java.sql.Timestamp ts = rs.getTimestamp(column);
    return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
  }
}
