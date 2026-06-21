package com.pythontopia.learner.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.pythontopia.PythontopiaApplication;
import com.pythontopia.learner.memory.MemoryEmbeddingRepository.ScoredEvent;
import java.util.List;
import java.util.Optional;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 记忆向量语义检索集成测试（计划中的 {@code MemoryRetrievalSemanticIT}；按本仓 surefire 约定改名 *
 * IntegrationTest 以确保被 mvn test 执行）。在真 pgvector 库上跑 V140 迁移，验证 upsert/find 往返、余弦
 * topK 顺序、(user) 隔离。维度 2048 对齐智谱 embedding-3。
 */
@Testcontainers
@SpringBootTest(classes = PythontopiaApplication.class)
class MemoryRetrievalSemanticIntegrationTest {

  private static final int DIM = 2048;

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("pythontopia")
          .withUsername("pytopia")
          .withPassword("pytopia");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    registry.add(
        "pythontopia.security.jwt.secret", () -> "test-secret-must-be-long-enough-for-hs256");
    registry.add("pythontopia.security.teacherInviteCode", () -> "test-teacher-code");
  }

  @Autowired MemoryEmbeddingRepository repository;
  @Autowired JdbcTemplate jdbc;

  @Test
  void searchByCosineRanksBySemanticSimilarityDescending() {
    long userId = insertUser("semantic-rank");
    long e1 = insertEvent(userId, "practice_failed");
    long e2 = insertEvent(userId, "practice_failed");
    long e3 = insertEvent(userId, "comeback_solved");
    repository.upsert(e1, axis(0), "bge-small-zh-v1.5"); // 与 query 完全同向 → cos 1.0
    repository.upsert(e2, axis(1), "bge-small-zh-v1.5"); // 与 query 正交 → cos 0.0
    repository.upsert(e3, mix(0.8, 0.6), "bge-small-zh-v1.5"); // 与 query 余弦 0.8

    List<ScoredEvent> hits = repository.searchByCosine(userId, axis(0), 3);

    assertThat(hits).extracting(ScoredEvent::eventId).containsExactly(e1, e3, e2);
    assertThat(hits.get(0).similarity()).isCloseTo(1.0, Offset.offset(1e-4));
    assertThat(hits.get(1).similarity()).isCloseTo(0.8, Offset.offset(5e-4));
    assertThat(hits.get(2).similarity()).isCloseTo(0.0, Offset.offset(1e-4));
  }

  @Test
  void searchByCosineIsolatesByUser() {
    long userA = insertUser("iso-a");
    long userB = insertUser("iso-b");
    long ea = insertEvent(userA, "practice_failed");
    long eb = insertEvent(userB, "practice_failed");
    repository.upsert(ea, axis(0), "bge-small-zh-v1.5");
    repository.upsert(eb, axis(0), "bge-small-zh-v1.5");

    List<ScoredEvent> hits = repository.searchByCosine(userA, axis(0), 10);

    assertThat(hits).extracting(ScoredEvent::eventId).containsExactly(ea);
  }

  @Test
  void upsertThenFindRoundTrips() {
    long userId = insertUser("roundtrip");
    long event = insertEvent(userId, "resource_consumed");

    repository.upsert(event, mix(0.6, 0.8), "bge-small-zh-v1.5");
    Optional<float[]> found = repository.find(event);

    assertThat(found).isPresent();
    assertThat(found.orElseThrow()).hasSize(DIM);
    assertThat(found.orElseThrow()[0]).isCloseTo(0.6f, Offset.offset(1e-4f));
    assertThat(found.orElseThrow()[1]).isCloseTo(0.8f, Offset.offset(5e-4f));
  }

  @Test
  void upsertOnConflictUpdatesEmbeddingWithoutAddingRow() {
    long userId = insertUser("conflict");
    long event = insertEvent(userId, "practice_failed");

    repository.upsert(event, axis(0), "model-old");
    repository.upsert(event, axis(1), "model-new");

    float[] found = repository.find(event).orElseThrow();
    assertThat(found[0]).isCloseTo(0.0f, Offset.offset(1e-4f));
    assertThat(found[1]).isCloseTo(1.0f, Offset.offset(1e-4f));
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM memory_embedding WHERE event_id = ?", Integer.class, event);
    assertThat(count).isEqualTo(1);
  }

  @Test
  void findReturnsEmptyForUnknownEvent() {
    assertThat(repository.find(-1L)).isEmpty();
  }

  private long insertUser(String username) {
    Long id =
        jdbc.queryForObject(
            "INSERT INTO users (username, display_name, password_hash, role) "
                + "VALUES (?, ?, 'x', 'STUDENT') RETURNING id",
            Long.class,
            "memit-" + username,
            username);
    return id == null ? 0L : id;
  }

  private long insertEvent(long userId, String eventType) {
    Long id =
        jdbc.queryForObject(
            "INSERT INTO learning_event_log (user_id, event_type, payload, source, importance) "
                + "VALUES (?, ?, '{}'::jsonb, 'backend', 3) RETURNING id",
            Long.class,
            userId,
            eventType);
    return id == null ? 0L : id;
  }

  private static float[] axis(int index) {
    float[] vector = new float[DIM];
    vector[index] = 1.0f;
    return vector;
  }

  /** 仅前两维非零的向量（调用方保证 a0² + a1² = 1 以便断言余弦值）。 */
  private static float[] mix(double a0, double a1) {
    float[] vector = new float[DIM];
    vector[0] = (float) a0;
    vector[1] = (float) a1;
    return vector;
  }
}
