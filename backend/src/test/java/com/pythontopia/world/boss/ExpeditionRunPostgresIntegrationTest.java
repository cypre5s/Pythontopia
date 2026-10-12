package com.pythontopia.world.boss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.world.boss.dto.ExpeditionRunDto;
import com.pythontopia.world.boss.dto.ExpeditionScoreSubmitDto;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** 真 PostgreSQL：快照不能换活动 runId，分数从判题账本读取，结算后新局获取新 id。 */
@Testcontainers
class ExpeditionRunPostgresIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:pg16")
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("pythontopia")
          .withUsername("pytopia")
          .withPassword("pytopia");

  private static JdbcTemplate jdbc;

  @BeforeAll
  static void migrate() {
    DriverManagerDataSource source = new DriverManagerDataSource();
    source.setDriverClassName("org.postgresql.Driver");
    source.setUrl(POSTGRES.getJdbcUrl());
    source.setUsername(POSTGRES.getUsername());
    source.setPassword(POSTGRES.getPassword());
    Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
    jdbc = new JdbcTemplate(source);
  }

  @Test
  void serverOwnsRunIdentityAndSettlementState() {
    long userId = createStudent();
    ExpeditionRunService runs = new ExpeditionRunService(jdbc, new ObjectMapper());
    ExpeditionRunDto initial = runs.save(userId, snapshot(null, 999_999));

    assertThat(initial.runId()).isNotBlank();
    assertThat(initial.score()).isZero();
    assertThatThrownBy(() -> runs.save(userId, snapshot(UUID.randomUUID().toString(), 0)))
        .isInstanceOf(BusinessException.class);

    jdbc.update(
        "INSERT INTO expedition_verified_attempt (user_id, run_id, problem_id, passed, score)"
            + " VALUES (?, ?::uuid, 'issued-problem', TRUE, 80)",
        userId,
        initial.runId());
    ExpeditionRunDto updated = runs.save(userId, snapshot(initial.runId(), 999_999));
    assertThat(updated.score()).isEqualTo(80);

    new ExpeditionScoreService(jdbc)
        .record(userId, new ExpeditionScoreSubmitDto(initial.runId()));
    assertThat(jdbc.queryForObject(
            "SELECT status FROM expedition_run WHERE user_id = ?", String.class, userId))
        .isEqualTo("SETTLED");
    assertThat(jdbc.queryForObject(
            "SELECT score FROM expedition_score WHERE user_id = ? AND run_id = ?::uuid",
            Integer.class,
            userId,
            initial.runId()))
        .isEqualTo(80);

    ExpeditionRunDto next = runs.save(userId, snapshot(null, 999_999));
    assertThat(next.runId()).isNotEqualTo(initial.runId());
    assertThat(next.score()).isZero();
  }

  private long createStudent() {
    return jdbc.queryForObject(
        "INSERT INTO users (username, display_name, password_hash, role)"
            + " VALUES (?, 'Student', 'x', 'STUDENT') RETURNING id",
        Long.class,
        "expedition-" + UUID.randomUUID());
  }

  private static ExpeditionRunDto snapshot(String runId, int score) {
    return new ExpeditionRunDto(
        "map", 1, List.of(), 10, null, 5, 5, 0, 0, score, List.of(), false, List.of(), runId);
  }
}
