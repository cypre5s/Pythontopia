package com.pythontopia.world.boss.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** 真 PostgreSQL + 全量 Flyway：锁定教师审核题库的隐藏题面、决策审计与版本约束。 */
@Testcontainers
class BossProblemReviewSchemaTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:pg16")
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("pythontopia")
          .withUsername("pytopia")
          .withPassword("pytopia");

  private static JdbcTemplate jdbcTemplate;

  @BeforeAll
  static void migrate() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.postgresql.Driver");
    dataSource.setUrl(POSTGRES.getJdbcUrl());
    dataSource.setUsername(POSTGRES.getUsername());
    dataSource.setPassword(POSTGRES.getPassword());
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .load()
        .migrate();
    jdbcTemplate = new JdbcTemplate(dataSource);
  }

  @Test
  void migrationCreatesAuditedPack43ReviewBank() {
    List<String> columns =
        jdbcTemplate.queryForList(
            "SELECT column_name FROM information_schema.columns"
                + " WHERE table_schema = 'public' AND table_name = 'boss_problem_review'"
                + " ORDER BY ordinal_position",
            String.class);

    assertThat(columns)
        .contains(
            "pack_id",
            "source_problem_id",
            "payload",
            "decision",
            "requester_id",
            "requested_at",
            "decider_id",
            "decided_at",
            "decision_reason",
            "version");
  }

  @Test
  void databaseRejectsDecisionWithoutCompleteAuditMetadata() {
    Long teacherId =
        jdbcTemplate.queryForObject(
            "INSERT INTO users (username, display_name, password_hash, role)"
                + " VALUES ('review-schema-teacher', 'Teacher', 'x', 'TEACHER') RETURNING id",
            Long.class);

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO boss_problem_review"
                        + " (pack_id, source_problem_id, chapter, stage, kc_code, difficulty,"
                        + " payload, decision, requester_id, decider_id)"
                        + " VALUES (43, 'source-1', 1, 1, 'PY43-KC-001', 2, '{}'::jsonb,"
                        + " 'APPROVED', ?, ?)",
                    teacherId,
                    teacherId))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }
}
