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

  private static final String DEFAULT_DIMENSIONS_JSON =
      """
      {
        "professional_background": {"score": 0.5, "evidence_count": 0, "tags": [], "source": "bootstrap_default"},
        "knowledge_base": {"score": 0.5, "evidence_count": 0, "tags": [], "source": "bootstrap_default"},
        "learning_goal": {"score": 0.5, "evidence_count": 0, "tags": [], "source": "bootstrap_default"},
        "cognitive_style": {"score": 0.5, "evidence_count": 0, "tags": [], "source": "bootstrap_default"},
        "learning_preference": {"score": 0.5, "evidence_count": 0, "tags": [], "source": "bootstrap_default"},
        "error_pattern": {"score": 0.5, "evidence_count": 0, "tags": [], "source": "bootstrap_default"},
        "practical_ability": {"score": 0.5, "evidence_count": 0, "tags": [], "source": "bootstrap_default"},
        "emotion_persistence": {"score": 0.5, "evidence_count": 0, "tags": [], "source": "bootstrap_default"}
      }
      """;

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
    return jdbcTemplate
        .query(
            """
            INSERT INTO profile_interview (user_id, session_id, status)
            VALUES (?, ?, 'OPEN')
            ON CONFLICT (user_id) WHERE status = 'OPEN'
            DO UPDATE SET session_id = profile_interview.session_id
            RETURNING session_id::text, status
            """,
            (rs, rowNum) ->
                new ProfileInterviewStartResponse(
                    rs.getString("session_id"), rs.getString("status"), List.of(), null),
            userId,
            sessionId)
        .stream()
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Failed to create or resume profile interview"));
  }

  public boolean ownsInterview(Long userId, String sessionId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM profile_interview WHERE user_id = ? AND session_id = ?::uuid AND status = 'OPEN'",
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

  public LearnerProfileDto ensureDefaultProfile(Long userId) {
    return findProfile(userId)
        .orElseGet(
            () -> {
              insertDefaultProfile(userId);
              return findProfile(userId)
                  .orElseThrow(
                      () ->
                          new IllegalStateException(
                              "Failed to create default learner_profile for user " + userId));
            });
  }

  private void insertDefaultProfile(Long userId) {
    jdbcTemplate.update(
        """
        INSERT INTO learner_profile (user_id, pack_id, dimensions, updated_at)
        VALUES (?, 43, ?::jsonb, now())
        ON CONFLICT (user_id) DO NOTHING
        """,
        userId,
        DEFAULT_DIMENSIONS_JSON);
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

  public boolean completeInterview(Long userId, String sessionId) {
    return jdbcTemplate.update(
        """
        UPDATE profile_interview
        SET status = 'COMPLETED', completed_at = now()
        WHERE user_id = ? AND session_id = ?::uuid AND status = 'OPEN'
        """,
        userId,
        sessionId) == 1;
  }

  public void abortInterview(Long userId, String sessionId) {
    jdbcTemplate.update(
        """
        UPDATE profile_interview
        SET status = 'ABORTED', completed_at = now()
        WHERE user_id = ? AND session_id = ?::uuid AND status = 'OPEN'
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
    return readDimensions("SELECT dimensions FROM learner_profile WHERE user_id = ?", userId);
  }

  /**
   * 与 {@link #findDimensions} 相同，但对 learner_profile 行加行级写锁（{@code FOR UPDATE}）。
   *
   * <p>dimensions 是整块 JSONB 的 read-modify-write：并发学习事件（如 {@code POST /api/learning/events} 与
   * 答疑事件同时到达同一学生）若各自读到同一份快照，后写者会整块覆盖前写者的维度更新。写路径统一走本方法，
   * 让同一学生的并发更新在同一事务边界内串行化。
   *
   * <p><b>必须在可写事务中调用</b>：PostgreSQL 在只读事务里执行 {@code SELECT ... FOR UPDATE} 会直接报错，
   * 因此读路径（getDimensions / profileBrief 等 {@code readOnly = true}）仍走 {@link #findDimensions}。
   */
  public Optional<JsonNode> findDimensionsForUpdate(Long userId) {
    return readDimensions(
        "SELECT dimensions FROM learner_profile WHERE user_id = ? FOR UPDATE", userId);
  }

  private Optional<JsonNode> readDimensions(String sql, Long userId) {
    return jdbcTemplate
        .query(sql, (rs, rowNum) -> rs.getString("dimensions"), userId)
        .stream()
        .findFirst()
        .map(this::tryReadJson)
        .filter(node -> node != null && !node.isMissingNode() && !node.isNull());
  }

  /**
   * 覆盖写入 learner_profile.dimensions（八维聚合），profile 行不存在时按默认值补建。
   *
   * <p>历史实现是裸 UPDATE：未走过 {@code POST /api/world/bootstrap} 的新注册学生没有 learner_profile 行，
   * 维度写入命中 0 行却静默成功，接口返回 200「已更新」而库里什么都没写。改为 upsert 后写入总能落库。
   *
   * @return 受影响行数；0 表示写入未落库，调用方不应据此写 revision 审计。
   */
  public int saveDimensions(Long userId, JsonNode dimensions) {
    String payload = writeJson(dimensions == null ? objectMapper.createObjectNode() : dimensions);
    return jdbcTemplate.update(
        """
        INSERT INTO learner_profile (user_id, pack_id, dimensions, updated_at)
        VALUES (?, 43, ?::jsonb, now())
        ON CONFLICT (user_id) DO UPDATE SET
          dimensions = EXCLUDED.dimensions,
          updated_at = now()
        """,
        userId,
        payload);
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
