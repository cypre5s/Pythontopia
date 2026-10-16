package com.pythontopia.world.boss;

import static org.assertj.core.api.Assertions.assertThat;

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
class BossObjectiveQuestionMigrationTest {

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
    DataSource source =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
    jdbc = new JdbcTemplate(source);
  }

  @Test
  void conceptOnlyKnowledgeCodesHaveThreeSupplementalObjectiveTypes() {
    for (String kcCode :
        new String[] {"PY43-KC-014", "PY43-KC-017", "PY43-KC-027", "PY43-KC-051"}) {
      assertThat(
              jdbc.queryForList(
                  "SELECT type FROM mainline_practice_question"
                      + " WHERE kc_code = ? AND required_for_stage = FALSE",
                  String.class,
                  kcCode))
          .as(kcCode)
          .containsExactlyInAnyOrder("CHOICE", "JUDGE", "FILL");
    }
  }

  @Test
  void supplementalQuestionsDoNotIncreaseRequiredMainlineQuestionCount() {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM mainline_practice_question WHERE required_for_stage = TRUE",
                Integer.class))
        .isEqualTo(96);
  }
}
