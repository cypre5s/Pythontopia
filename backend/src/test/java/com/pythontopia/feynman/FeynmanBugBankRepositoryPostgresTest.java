package com.pythontopia.feynman;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class FeynmanBugBankRepositoryPostgresTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:pg16")
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("pythontopia")
          .withUsername("pytopia")
          .withPassword("pytopia");

  private static FeynmanBugBankRepository repository;
  private static JdbcTemplate jdbc;

  @BeforeAll
  static void migrate() {
    DataSource source =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
    jdbc = new JdbcTemplate(source);
    repository = new FeynmanBugBankRepository(jdbc, new ObjectMapper());
  }

  @Test
  void persistsUniqueSampleAndReusesItAtRequestedDifficulty() {
    FeynmanGraphClient.BugExposure easy = sample("easy");
    FeynmanGraphClient.BugExposure hard = sample("hard");
    assertThat(repository.insertIfAbsent("PY43-KC-016", "循环", easy)).isTrue();
    assertThat(repository.insertIfAbsent("PY43-KC-016", "循环", easy)).isFalse();
    assertThat(repository.insertIfAbsent("PY43-KC-016", "循环", hard)).isTrue();

    assertThat(repository.findOne("PY43-KC-016", "hard"))
        .get()
        .extracting(FeynmanBugBankRepository.BugSample::difficulty)
        .isEqualTo("hard");
    assertThat(repository.findOne("PY43-KC-016", "medium"))
        .get()
        .extracting(FeynmanBugBankRepository.BugSample::difficulty)
        .isEqualTo("easy");
    assertThat(jdbc.queryForObject("SELECT sum(use_count) FROM feynman_bug_bank", Long.class))
        .isEqualTo(2L);
  }

  private static FeynmanGraphClient.BugExposure sample(String difficulty) {
    return new FeynmanGraphClient.BugExposure(
        "generic_beginner_bug",
        "topic_specific_bug",
        "a = 1\nb = 2\nc = 3\nd = 4\ne = a - b\nprint(a)\nprint(e)",
        "a = 1\nb = 2\nc = 3\nd = 4\ne = a + b\nprint(a)\nprint(e)",
        List.of(4),
        "检查运算符。",
        difficulty,
        true);
  }
}
