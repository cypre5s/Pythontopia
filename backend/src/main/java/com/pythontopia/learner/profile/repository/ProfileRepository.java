package com.pythontopia.learner.profile.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.learner.profile.dto.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProfileRepository {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public ProfileRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public Optional<ProfileInterviewStartResponse> findOpenInterview(Long userId) {
    return jdbcTemplate
        .query(
            """
            SELECT session_id, status
            FROM profile_interview
            WHERE user_id = ? AND status = 'OPEN'
            ORDER BY started_at DESC
            LIMIT 1
            """,
            (rs, rowNum) ->
                new ProfileInterviewStartResponse(
                    rs.getString("session_id"), rs.getString("status"), List.of(), null),
            userId)
        .stream()
        .findFirst();
  }

  public ProfileInterviewStartResponse createInterview(Long userId) {
    UUID sessionId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO profile_interview (user_id, session_id, status)
        VALUES (?, ?, 'OPEN')
        """,
        userId,
        sessionId);
    return new ProfileInterviewStartResponse(sessionId.toString(), "OPEN", List.of(), null);
  }

  public boolean ownsInterview(Long userId, String sessionId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM profile_interview WHERE user_id = ? AND session_id = ?::uuid",
            Integer.class,
            userId,
            sessionId);
    return count != null && count > 0;
  }

  public Optional<LearnerProfileDto> findProfile(Long userId) {
    return jdbcTemplate
        .query(
            """
            SELECT id, user_id, pack_id, major, base_level, learning_goal, cognitive_style,
                   learning_preference, error_patterns, practice_capability,
                   emotion_persistence, narrative_summary
            FROM learner_profile
            WHERE user_id = ?
            """,
            (rs, rowNum) -> mapProfile(rs),
            userId)
        .stream()
        .findFirst();
  }

  public void upsertProfile(Long userId, LearnerProfileDto draft) {
    jdbcTemplate.update(
        """
        INSERT INTO learner_profile (
          user_id, pack_id, major, base_level, learning_goal, cognitive_style,
          learning_preference, error_patterns, practice_capability,
          emotion_persistence, narrative_summary, updated_at
        )
        VALUES (?, 43, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?, now())
        ON CONFLICT (user_id) DO UPDATE SET
          major = EXCLUDED.major,
          base_level = EXCLUDED.base_level,
          learning_goal = EXCLUDED.learning_goal,
          cognitive_style = EXCLUDED.cognitive_style,
          learning_preference = EXCLUDED.learning_preference,
          error_patterns = EXCLUDED.error_patterns,
          practice_capability = EXCLUDED.practice_capability,
          emotion_persistence = EXCLUDED.emotion_persistence,
          narrative_summary = EXCLUDED.narrative_summary,
          updated_at = now()
        """,
        userId,
        draft.major(),
        writeJson(draft.baseLevel()),
        writeJson(draft.learningGoal()),
        writeJson(draft.cognitiveStyle()),
        writeJson(draft.learningPreference()),
        writeJson(draft.errorPatterns()),
        writeJson(draft.practiceCapability()),
        writeJson(draft.emotionPersistence()),
        draft.narrativeSummary());
  }

  public void completeInterview(Long userId, String sessionId) {
    jdbcTemplate.update(
        """
        UPDATE profile_interview
        SET status = 'COMPLETED', completed_at = now()
        WHERE user_id = ? AND session_id = ?::uuid
        """,
        userId,
        sessionId);
  }

  public void abortInterview(Long userId, String sessionId) {
    jdbcTemplate.update(
        """
        UPDATE profile_interview
        SET status = 'ABORTED', completed_at = now()
        WHERE user_id = ? AND session_id = ?::uuid
        """,
        userId,
        sessionId);
  }

  public void addRevision(Long userId, String source, Object diff) {
    addRevision(userId, source, diff, null);
  }

  public void addRevision(Long userId, String source, Object diff, Long evaluationId) {
    jdbcTemplate.update(
        """
        INSERT INTO profile_revision (user_id, source, diff, evaluation_id)
        VALUES (?, ?, ?::jsonb, ?)
        """,
        userId,
        source,
        writeJson(diff),
        evaluationId);
  }

  /**
   * 读取 learner_profile.dimensions JSONB（八维画像聚合字段）。
   *
   * <p>无 profile 行、{@code dimensions} 为 NULL 或空串时返回 {@link Optional#empty()}；JSON 非法时抛
   * {@link IllegalStateException}。
   *
   * <p>{@code dimensions} 列尚未创建时底层 SQL 抛 {@link org.springframework.jdbc.BadSqlGrammarException}，
   * 本方法刻意不吞，以便调用方（PassiveProfileService / PassiveProfileRevisionListener）显式降级。
   */
  public Optional<JsonNode> findDimensions(Long userId) {
    return jdbcTemplate
        .query(
            "SELECT dimensions FROM learner_profile WHERE user_id = ?",
            (rs, rowNum) -> rs.getString("dimensions"),
            userId)
        .stream()
        .findFirst()
        .map(this::tryReadJson)
        .filter(node -> node != null && !node.isMissingNode() && !node.isNull());
  }

  /**
   * 覆盖写入 learner_profile.dimensions（八维聚合）。需要 profile 行存在；不存在时静默跳过（依赖 INTERVIEW 流程先建 profile）。
   */
  public void saveDimensions(Long userId, JsonNode dimensions) {
    String payload = writeJson(dimensions == null ? objectMapper.createObjectNode() : dimensions);
    jdbcTemplate.update(
        """
        UPDATE learner_profile
        SET dimensions = ?::jsonb,
            updated_at = now()
        WHERE user_id = ?
        """,
        payload,
        userId);
  }

  private JsonNode tryReadJson(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readTree(json);
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to read learner_profile.dimensions JSON", exception);
    }
  }

  public List<ProfileRevisionDto> findRevisions(Long userId, int limit) {
    return jdbcTemplate.query(
        """
        SELECT id, source, diff, created_at::text AS created_at
        FROM profile_revision
        WHERE user_id = ?
        ORDER BY created_at DESC
        LIMIT ?
        """,
        (rs, rowNum) ->
            new ProfileRevisionDto(
                rs.getLong("id"),
                rs.getString("source"),
                readJson(rs.getString("diff")),
                rs.getString("created_at")),
        userId,
        limit);
  }

  private LearnerProfileDto mapProfile(ResultSet rs) throws SQLException {
    return new LearnerProfileDto(
        rs.getLong("id"),
        rs.getLong("user_id"),
        rs.getLong("pack_id"),
        rs.getString("major"),
        read(rs.getString("base_level"), BaseLevelDto.class),
        read(rs.getString("learning_goal"), LearningGoalDto.class),
        read(rs.getString("cognitive_style"), CognitiveStyleDto.class),
        read(rs.getString("learning_preference"), LearningPreferenceDto.class),
        read(rs.getString("error_patterns"), ErrorPatternsDto.class),
        read(rs.getString("practice_capability"), PracticeCapabilityDto.class),
        read(rs.getString("emotion_persistence"), EmotionPersistenceDto.class),
        rs.getString("narrative_summary"));
  }

  private <T> T read(String json, Class<T> type) {
    try {
      if (json == null || json.isBlank() || "{}".equals(json)) {
        return null;
      }
      return objectMapper.readValue(json, type);
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to read profile JSON", exception);
    }
  }

  private JsonNode readJson(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to read revision JSON", exception);
    }
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value == null ? objectMapper.createObjectNode() : value);
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to write profile JSON", exception);
    }
  }
}
