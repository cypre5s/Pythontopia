package com.pythontopia.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class AiRuntimePlatformSnapshotRepositoryTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:pg16")
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("pythontopia")
          .withUsername("pytopia")
          .withPassword("pytopia");

  private static AiRuntimePlatformSnapshotRepository repository;
  private static JdbcTemplate jdbc;

  @BeforeAll
  static void migrateAndWire() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.postgresql.Driver");
    dataSource.setUrl(POSTGRES.getJdbcUrl());
    dataSource.setUsername(POSTGRES.getUsername());
    dataSource.setPassword(POSTGRES.getPassword());
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    jdbc = new JdbcTemplate(dataSource);
    repository = new AiRuntimePlatformSnapshotRepository(jdbc);
  }

  @BeforeEach
  void clearRuntimeEvidence() {
    jdbc.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
  }

  @Test
  void emptyMigratedDatabaseReturnsOnlyZeroAggregates() {
    AiRuntimePlatformSnapshotRepository.PlatformSnapshot snapshot = repository.snapshot();

    assertThat(snapshot)
        .isEqualTo(new AiRuntimePlatformSnapshotRepository.PlatformSnapshot(0, 0, 0, 0, 0));
  }

  @Test
  void snapshotCountsOnlyActiveStudentsAndDeduplicatesLearnersAndKnowledgeComponents() {
    long activeOne = insertUser("active-one", "STUDENT", "ACTIVE");
    long activeTwo = insertUser("active-two", "STUDENT", "ACTIVE");
    long disabledStudent = insertUser("disabled", "STUDENT", "DISABLED");
    long teacher = insertUser("teacher", "TEACHER", "ACTIVE");

    insertLearningEvent(activeOne, "practice_failed", "{\"kcCode\":\"PY43-KC-001\"}");
    insertLearningEvent(activeOne, "practice_failed", "{\"kcCodes\":[\"PY43-KC-001\"]}");
    insertLearningEvent(disabledStudent, "practice_failed", "{\"kcCode\":\"PY43-KC-099\"}");
    insertLearningEvent(teacher, "practice_failed", "{\"kcCode\":\"PY43-KC-100\"}");
    jdbc.update(
        """
        INSERT INTO kc_mastery_event(user_id, kc_code, source, delta)
        VALUES (?, 'PY43-KC-002', 'PRACTICE_PASS', 0.1)
        """,
        activeTwo);
    insertResourceRun(activeOne);
    insertResourceRun(activeTwo);
    insertResourceRun(disabledStudent);

    AiRuntimePlatformSnapshotRepository.PlatformSnapshot snapshot = repository.snapshot();

    assertThat(snapshot)
        .isEqualTo(new AiRuntimePlatformSnapshotRepository.PlatformSnapshot(2, 2, 2, 2, 2));
  }

  private long insertUser(String username, String role, String status) {
    return jdbc.queryForObject(
        """
        INSERT INTO users(username, display_name, password_hash, role, status)
        VALUES (?, ?, 'test-hash', ?, ?)
        RETURNING id
        """,
        Long.class,
        username,
        username,
        role,
        status);
  }

  private void insertLearningEvent(long userId, String eventType, String payload) {
    jdbc.update(
        """
        INSERT INTO learning_event_log(user_id, event_type, payload)
        VALUES (?, ?, ?::jsonb)
        """,
        userId,
        eventType,
        payload);
  }

  private void insertResourceRun(long userId) {
    jdbc.update(
        """
        INSERT INTO resource_run(user_id, agent_name, scenario, status)
        VALUES (?, 'ResourceDesignerAgent', 'runtime-aggregate-test', 'SUCCESS')
        """,
        userId);
  }
}
