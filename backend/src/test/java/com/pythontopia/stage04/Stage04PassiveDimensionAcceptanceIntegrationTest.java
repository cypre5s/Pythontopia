package com.pythontopia.stage04;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.PythontopiaApplication;
import com.pythontopia.learner.event.LearningEvent;
import java.math.BigDecimal;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 阶段 M4 - V110 migration + PassiveProfileService 端到端验收。
 *
 * <p>覆盖：
 *
 * <ul>
 *   <li>V110 真的为 {@code learner_profile} 加了 {@code dimensions} JSONB 列 + GIN 索引；
 *   <li>{@link LearningEvent.PracticeSucceeded} 事件最终落到 {@code dimensions->'practical_ability'->>'score'}
 *       并写入 {@code profile_revision} 审计；
 *   <li>{@link LearningEvent.PracticeFailed} 事件最终落到 {@code error_pattern}，{@code tags} 数组追加 failureKind；
 *   <li>{@link LearningEvent.DebugResolved} 事件以负 delta 降低 {@code error_pattern.score}；
 *   <li>{@link LearningEvent.ResourceCheckCompleted} 事件提升 {@code knowledge_base.score} 并记录 metadata。
 * </ul>
 *
 * <p>注：测试绕过 INTERVIEW 流程，直接 INSERT 一行 {@code learner_profile}（pack_id=43，dimensions 取默认
 * '{}'::jsonb），因此事件处理时 {@code findDimensions} 读到空 ObjectNode，PassiveProfileService 会以
 * {@link com.pythontopia.learner.profile.service.PassiveProfileService 默认 score=0.5} 起算。
 */
@Testcontainers
@SpringBootTest(classes = PythontopiaApplication.class)
class Stage04PassiveDimensionAcceptanceIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:pg16")
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("pythontopia")
          .withUsername("pytopia")
          .withPassword("pytopia");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    registry.add(
        "pythontopia.security.jwt.secret", () -> "test-secret-must-be-long-enough-for-hs256");
    registry.add("pythontopia.security.teacherInviteCode", () -> "test-teacher-code");
    registry.add("pythontopia.services.graphUrl", () -> "http://graph.test");
  }

  @Autowired ApplicationEventPublisher publisher;

  @Autowired JdbcTemplate jdbc;

  @Autowired ObjectMapper objectMapper;

  @Test
  void v111MigrationCreatesDimensionsColumnAndGinIndex() {
    Boolean columnExists =
        jdbc.queryForObject(
            """
            SELECT EXISTS (
              SELECT 1 FROM information_schema.columns
              WHERE table_name = 'learner_profile' AND column_name = 'dimensions'
            )
            """,
            Boolean.class);
    assertThat(columnExists).as("learner_profile.dimensions column").isTrue();

    String dataType =
        jdbc.queryForObject(
            """
            SELECT data_type FROM information_schema.columns
            WHERE table_name = 'learner_profile' AND column_name = 'dimensions'
            """,
            String.class);
    assertThat(dataType).as("dimensions data_type").isEqualTo("jsonb");

    Boolean indexExists =
        jdbc.queryForObject(
            """
            SELECT EXISTS (
              SELECT 1 FROM pg_indexes
              WHERE tablename = 'learner_profile'
                AND indexname = 'ix_learner_profile_dimensions_gin'
            )
            """,
            Boolean.class);
    assertThat(indexExists).as("GIN index on dimensions").isTrue();
  }

  @Test
  void practiceSucceededIncrementsPracticalAbilityAndAuditsRevision() throws Exception {
    Long userId = insertUserWithProfile("dim-stu-01");

    publisher.publishEvent(new LearningEvent.PracticeSucceeded(userId, "PY43-KC-001", 101L, "medium"));

    JsonNode dimensions = readDimensions(userId);
    JsonNode practical = dimensions.path("practical_ability");
    assertThat(practical.path("score").asDouble())
        .as("practical_ability.score after +0.02")
        .isEqualTo(0.52, Offset.offset(1e-9));
    assertThat(practical.path("evidence_count").asInt())
        .as("practical_ability.evidence_count")
        .isEqualTo(1);
    assertThat(practical.path("last_updated").asText()).as("last_updated").isNotBlank();

    Integer revCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM profile_revision WHERE user_id = ? AND source = 'PRACTICE_PASS'",
            Integer.class,
            userId);
    assertThat(revCount).as("profile_revision PRACTICE_PASS row").isEqualTo(1);

    String diffJson =
        jdbc.queryForObject(
            """
            SELECT diff::text FROM profile_revision
            WHERE user_id = ? AND source = 'PRACTICE_PASS'
            ORDER BY created_at DESC LIMIT 1
            """,
            String.class,
            userId);
    JsonNode diff = objectMapper.readTree(diffJson);
    assertThat(diff.path("dimensionKey").asText()).isEqualTo("practical_ability");
    assertThat(diff.path("scoreDelta").asDouble()).isEqualTo(0.02);
    assertThat(diff.path("finalScore").asDouble()).isEqualTo(0.52, Offset.offset(1e-9));
  }

  @Test
  void practiceFailedAppendsErrorPatternTagAndAuditsRevision() throws Exception {
    Long userId = insertUserWithProfile("dim-stu-02");

    publisher.publishEvent(new LearningEvent.PracticeFailed(userId, "PY43-KC-002", 102L, "WA", "medium"));

    JsonNode dimensions = readDimensions(userId);
    JsonNode err = dimensions.path("error_pattern");
    assertThat(err.path("score").asDouble())
        .as("error_pattern.score after +0.02")
        .isEqualTo(0.52, Offset.offset(1e-9));
    assertThat(err.path("tags").isArray()).isTrue();
    assertThat(err.path("tags").get(0).asText()).isEqualTo("WA");

    Integer revCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM profile_revision WHERE user_id = ? AND source = 'PRACTICE_FAIL'",
            Integer.class,
            userId);
    assertThat(revCount).as("profile_revision PRACTICE_FAIL row").isEqualTo(1);
  }

  @Test
  void debugResolvedReducesErrorPatternScore() throws Exception {
    Long userId = insertUserWithProfile("dim-stu-03");

    publisher.publishEvent(
        new LearningEvent.DebugResolved(
            userId, "PY43-KC-003", 103L, "integration-debug:" + userId));

    JsonNode dimensions = readDimensions(userId);
    assertThat(dimensions.path("error_pattern").path("score").asDouble())
        .as("error_pattern.score after -0.02")
        .isEqualTo(0.48, Offset.offset(1e-9));

    Integer revCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM profile_revision WHERE user_id = ? AND source = 'DEBUG_FIX'",
            Integer.class,
            userId);
    assertThat(revCount).as("profile_revision DEBUG_FIX row").isEqualTo(1);
  }

  @Test
  void resourceCheckPassedIncreasesKnowledgeBaseAndCarriesMetadata() throws Exception {
    Long userId = insertUserWithProfile("dim-stu-04");

    publisher.publishEvent(
        new LearningEvent.ResourceCheckCompleted(
            userId,
            401L,
            801L,
            "PY43-KC-004",
            "MICRO_QUIZ",
            true,
            new BigDecimal("0.05"),
            null));

    JsonNode dimensions = readDimensions(userId);
    JsonNode kb = dimensions.path("knowledge_base");
    assertThat(kb.path("score").asDouble())
        .as("knowledge_base.score after resource check evidence")
        .isEqualTo(0.515, Offset.offset(1e-9));
    assertThat(kb.path("evidence_count").asInt()).isEqualTo(1);
    assertThat(kb.path("kcCode").asText()).isEqualTo("PY43-KC-004");
    assertThat(kb.path("checkId").asLong()).isEqualTo(401L);
    assertThat(kb.path("resourceId").asLong()).isEqualTo(801L);
    assertThat(kb.path("evidenceType").asText()).isEqualTo("MICRO_QUIZ");

    Integer revCount =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM profile_revision
            WHERE user_id = ? AND source = 'RESOURCE_FEEDBACK'
            """,
            Integer.class,
            userId);
    assertThat(revCount).as("profile_revision RESOURCE_FEEDBACK row").isEqualTo(1);
  }

  private Long insertUserWithProfile(String username) {
    jdbc.update(
        """
        INSERT INTO users (username, display_name, password_hash, role)
        VALUES (?, ?, '$2a$10$placeholder.hash.not.used.in.test', 'STUDENT')
        """,
        username,
        username);
    Long userId =
        jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
    jdbc.update("INSERT INTO learner_profile (user_id, pack_id) VALUES (?, 43)", userId);
    return userId;
  }

  private JsonNode readDimensions(Long userId) throws Exception {
    String dimensionsJson =
        jdbc.queryForObject(
            "SELECT dimensions::text FROM learner_profile WHERE user_id = ?",
            String.class,
            userId);
    return objectMapper.readTree(dimensionsJson);
  }
}
