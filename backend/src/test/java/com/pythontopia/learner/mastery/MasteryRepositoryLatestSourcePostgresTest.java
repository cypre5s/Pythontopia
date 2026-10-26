package com.pythontopia.learner.mastery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MasteryRepositoryLatestSourcePostgresTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("pythontopia")
          .withUsername("pytopia")
          .withPassword("pytopia");

  private static JdbcTemplate jdbc;
  private MasteryRepository repository;

  @BeforeAll
  static void createSchema() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.postgresql.Driver");
    dataSource.setUrl(POSTGRES.getJdbcUrl());
    dataSource.setUsername(POSTGRES.getUsername());
    dataSource.setPassword(POSTGRES.getPassword());
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute(
        "CREATE TABLE kc_mastery_event (id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL, "
            + "kc_code VARCHAR(64) NOT NULL, source VARCHAR(32) NOT NULL, "
            + "created_at TIMESTAMPTZ NOT NULL)");
  }

  @BeforeEach
  void resetRows() {
    jdbc.execute("TRUNCATE kc_mastery_event RESTART IDENTITY");
    repository = new MasteryRepository(jdbc);
  }

  @Test
  void picksHighestEventIdWhenTimestampsTie() {
    OffsetDateTime createdAt = OffsetDateTime.of(2026, 10, 25, 0, 0, 0, 0, ZoneOffset.UTC);
    jdbc.update(
        "INSERT INTO kc_mastery_event (user_id, kc_code, source, created_at) "
            + "VALUES (7, 'PY43-KC-001', 'PRACTICE_PASS', ?), "
            + "(7, 'PY43-KC-001', 'PRACTICE_FAIL', ?)",
        createdAt,
        createdAt);

    assertThat(repository.findLatestEventSourcesByKc(7L))
        .containsEntry("PY43-KC-001", MasterySource.PRACTICE_FAIL);
  }
}
