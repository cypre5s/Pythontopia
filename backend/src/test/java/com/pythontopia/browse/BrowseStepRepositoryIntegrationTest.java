package com.pythontopia.browse;

import static org.assertj.core.api.Assertions.assertThat;

import com.pythontopia.browse.entity.BrowseRunStatus;
import com.pythontopia.browse.entity.BrowseStepEntity;
import com.pythontopia.browse.repository.BrowseRunRepository;
import com.pythontopia.browse.repository.BrowseStepRepository;
import com.pythontopia.browse.service.BrowseRunPersistenceService;
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

@Testcontainers
class BrowseStepRepositoryIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:pg16")
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("pythontopia")
          .withUsername("pytopia")
          .withPassword("pytopia");

  private static JdbcTemplate jdbcTemplate;
  private static BrowseRunRepository runRepository;
  private static BrowseStepRepository stepRepository;

  @BeforeAll
  static void migrateAndWire() {
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
    runRepository = new BrowseRunRepository(jdbcTemplate);
    stepRepository = new BrowseStepRepository(jdbcTemplate);
  }

  @Test
  void v192RoundTripsStructuredPageEvidence() {
    jdbcTemplate.update(
        """
        INSERT INTO users (username, display_name, password_hash, role)
        VALUES (?, 'Browse Test', 'hash', 'STUDENT')
        """,
        "browse-v192-" + System.nanoTime());
    Long userId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM users WHERE username LIKE 'browse-v192-%' ORDER BY id DESC LIMIT 1",
            Long.class);
    var run =
        runRepository.insert(
            userId, "查证 sorted", BrowseRunStatus.SUCCESS, "summary", false, null);

    stepRepository.insertAll(
        run.id(),
        List.of(
            new BrowseStepEntity(
                null,
                run.id(),
                0,
                "read",
                "https://docs.python.org/3/howto/sorting.html",
                "legacy summary",
                "Sorting HOW TO",
                "Sorts are guaranteed to be stable.")));

    List<BrowseStepEntity> stored = stepRepository.findByRunId(run.id());

    assertThat(stored).hasSize(1);
    assertThat(stored.get(0).title()).isEqualTo("Sorting HOW TO");
    assertThat(stored.get(0).snippet()).isEqualTo("Sorts are guaranteed to be stable.");
    assertThat(stored.get(0).summary()).isEqualTo("legacy summary");
  }

  @Test
  void cleanupExpiredRunsDeletesOldHistoryAndCascadesSteps() {
    long userId = createStudent();
    var expired =
        runRepository.insert(
            userId, "old goal", BrowseRunStatus.PENDING, "", false, null);
    var retained =
        runRepository.insert(
            userId, "recent goal", BrowseRunStatus.SUCCESS, "summary", false, null);
    stepRepository.insertAll(
        expired.id(), List.of(new BrowseStepEntity(null, expired.id(), 0, "read", "", "old", "", "old")));
    stepRepository.insertAll(
        retained.id(),
        List.of(new BrowseStepEntity(null, retained.id(), 0, "read", "", "recent", "", "recent")));
    jdbcTemplate.update(
        "UPDATE browse_run SET created_at = now() - interval '91 days' WHERE id = ?", expired.id());

    new BrowseRunPersistenceService(runRepository, stepRepository).cleanupExpiredRuns();

    assertThat(runRepository.findByIdForUser(userId, expired.id())).isEmpty();
    assertThat(runRepository.findByIdForUser(userId, retained.id())).isPresent();
    assertThat(stepRepository.findByRunId(expired.id())).isEmpty();
    assertThat(stepRepository.findByRunId(retained.id())).hasSize(1);
  }

  private long createStudent() {
    jdbcTemplate.update(
        "INSERT INTO users (username, display_name, password_hash, role) VALUES (?, 'Browse Test', 'hash', 'STUDENT')",
        "browse-retention-" + System.nanoTime());
    return jdbcTemplate.queryForObject(
        "SELECT id FROM users WHERE username LIKE 'browse-retention-%' ORDER BY id DESC LIMIT 1",
        Long.class);
  }
}
