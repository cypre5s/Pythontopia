package com.pythontopia.learner.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.pythontopia.learner.evaluation.repository.EvaluationRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class EvaluationRepositoryWeeklyEligibilityPostgresTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("pythontopia")
          .withUsername("pytopia")
          .withPassword("pytopia");

  private static JdbcTemplate jdbc;
  private EvaluationRepository repository;

  @BeforeAll
  static void createSchema() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.postgresql.Driver");
    dataSource.setUrl(POSTGRES.getJdbcUrl());
    dataSource.setUsername(POSTGRES.getUsername());
    dataSource.setPassword(POSTGRES.getPassword());
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, role VARCHAR(20) NOT NULL)");
    jdbc.execute("CREATE TABLE practice_submission (user_id BIGINT, submitted_at TIMESTAMPTZ)");
    jdbc.execute("CREATE TABLE bug_mine_session (user_id BIGINT, opened_at TIMESTAMPTZ)");
    jdbc.execute("CREATE TABLE learning_resource (user_id BIGINT, created_at TIMESTAMPTZ)");
    jdbc.execute("CREATE TABLE kc_mastery_event (user_id BIGINT, created_at TIMESTAMPTZ)");
    jdbc.execute("CREATE TABLE learner_evaluation (user_id BIGINT, created_at TIMESTAMPTZ)");
  }

  @BeforeEach
  void resetRows() {
    jdbc.execute(
        "TRUNCATE users, practice_submission, bug_mine_session, learning_resource, "
            + "kc_mastery_event, learner_evaluation");
    repository = new EvaluationRepository(jdbc);
  }

  @Test
  void filtersRecentReportsAndInactiveAccountsInOneQuery() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    OffsetDateTime activitySince = now.minusDays(7);
    OffsetDateTime evaluationSince = now.minusDays(6);
    jdbc.update(
        "INSERT INTO users (id, role) VALUES (1, 'STUDENT'), (2, 'STUDENT'), "
            + "(3, 'STUDENT'), (4, 'STUDENT'), (5, 'TEACHER')");
    jdbc.update(
        "INSERT INTO learning_resource (user_id, created_at) VALUES "
            + "(1, ?), (2, ?), (3, ?), (4, ?), (5, ?)",
        activitySince.plusSeconds(1),
        activitySince.plusSeconds(1),
        activitySince.plusSeconds(1),
        activitySince.minusSeconds(1),
        activitySince.plusSeconds(1));
    jdbc.update(
        "INSERT INTO learner_evaluation (user_id, created_at) VALUES (2, ?), (3, ?)",
        evaluationSince.plusSeconds(1),
        evaluationSince.minusSeconds(1));

    List<Long> eligible =
        repository.findActiveLearnerIdsWithoutRecentEvaluation(activitySince, evaluationSince);

    assertThat(eligible).containsExactly(1L, 3L);
  }
}
