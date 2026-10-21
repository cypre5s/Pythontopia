package com.pythontopia.world.boss;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Verifies V210 preserves the generation budget represented by legacy node-bound rows. */
@Testcontainers
class ExpeditionGenerationBudgetMigrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("pythontopia")
          .withUsername("pytopia")
          .withPassword("pytopia");

  private static DriverManagerDataSource source;
  private static JdbcTemplate jdbc;

  @BeforeAll
  static void migrateToV209() {
    source = new DriverManagerDataSource();
    source.setDriverClassName("org.postgresql.Driver");
    source.setUrl(POSTGRES.getJdbcUrl());
    source.setUsername(POSTGRES.getUsername());
    source.setPassword(POSTGRES.getPassword());
    Flyway.configure()
        .dataSource(source)
        .locations("classpath:db/migration")
        .target(MigrationVersion.fromVersion("209"))
        .load()
        .migrate();
    jdbc = new JdbcTemplate(source);
  }

  @Test
  void backfillsAtMostThreeGenerationSlotsPerNodeAndBossPhase() {
    long userId =
        jdbc.queryForObject(
            "INSERT INTO users (username, display_name, password_hash, role)"
                + " VALUES ('generation-budget-migration', 'Student', 'x', 'STUDENT') RETURNING id",
            Long.class);
    String runId = "2b7da3e9-3327-4eb4-9961-d1352a4c6998";
    jdbc.update(
        "INSERT INTO expedition_run (user_id, run_id, phase, hp, max_hp, nodes)"
            + " VALUES (?, ?::uuid, 'map', 5, 5,"
            + " '[{\"id\":\"N1\",\"kind\":\"challenge\"},"
            + " {\"id\":\"B1\",\"kind\":\"boss\",\"bossPhase\":2}]'::jsonb)",
        userId,
        runId);

    insertProblem(userId, runId, "N1", "normal-1", "FAILED", 4);
    insertProblem(userId, runId, "B1", "issue-boss-phase-1-old", "CONSUMED", 2);
    insertProblem(userId, runId, "B1", "issue-boss-phase-2-old", "READY", 1);
    insertProblem(userId, runId, "B1", "approved-bank-old", "READY", 1);
    for (int i = 0; i < 37; i++) {
      insertProblem(userId, runId, "legacy-" + i, "legacy-problem-" + i, "FAILED", 1);
    }

    Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();

    assertThat(countSlots(userId, runId, "N1", null)).isEqualTo(3);
    assertThat(countSlots(userId, runId, "B1", 1)).isEqualTo(2);
    assertThat(countSlots(userId, runId, "B1", 2)).isEqualTo(2);
    assertThat(countRunSlots(userId, runId)).isEqualTo(36);
    assertThat(
            jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE version = '210'",
                String.class))
        .isEqualTo("210");
  }

  private void insertProblem(
      long userId, String runId, String nodeId, String problemId, String status, int attemptCount) {
    jdbc.update(
        "INSERT INTO pregenerated_problem"
            + " (user_id, chapter, stage, kc_code, difficulty, problem_id, payload, status,"
            + " attempt_count, expedition_run_id, expedition_node_id)"
            + " VALUES (?, 1, 1, ?, 1, ?, CASE WHEN ? = 'READY' THEN '{}'::jsonb ELSE NULL END,"
            + " ?, ?, ?::uuid, ?)",
        userId,
        problemId,
        problemId,
        status,
        status,
        attemptCount,
        runId,
        nodeId);
  }

  private int countSlots(long userId, String runId, String nodeId, Integer bossPhase) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM expedition_problem_generation_attempt"
            + " WHERE user_id = ? AND run_id = ?::uuid AND node_id = ?"
            + " AND boss_phase IS NOT DISTINCT FROM ?",
        Integer.class,
        userId,
        runId,
        nodeId,
        bossPhase);
  }

  private int countRunSlots(long userId, String runId) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM expedition_problem_generation_attempt"
            + " WHERE user_id = ? AND run_id = ?::uuid",
        Integer.class,
        userId,
        runId);
  }
}
