package com.pythontopia.learner.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.learner.profile.dto.DimensionDelta;
import com.pythontopia.learner.profile.dto.DimensionUpdateResult;
import com.pythontopia.learner.profile.repository.ProfileRepository;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class ProfileDimensionProjectionOutboxPostgresTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:pg16")
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("pythontopia")
          .withUsername("pytopia")
          .withPassword("pytopia");

  private static JdbcTemplate jdbc;
  private static DataSourceTransactionManager transactionManager;
  private static ObjectMapper objectMapper;
  private long userId;

  @BeforeAll
  static void migrate() {
    DriverManagerDataSource source = new DriverManagerDataSource();
    source.setDriverClassName("org.postgresql.Driver");
    source.setUrl(POSTGRES.getJdbcUrl());
    source.setUsername(POSTGRES.getUsername());
    source.setPassword(POSTGRES.getPassword());
    jdbc = new JdbcTemplate((DataSource) source);
    transactionManager = new DataSourceTransactionManager(source);
    objectMapper = new ObjectMapper().findAndRegisterModules();
    Flyway.configure()
        .dataSource(source)
        .locations("classpath:db/migration")
        .target(MigrationVersion.fromVersion("207"))
        .load()
        .migrate();
  }

  @BeforeEach
  void createStudent() {
    jdbc.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
    userId =
        jdbc.queryForObject(
            "INSERT INTO users(username, display_name, password_hash, role)"
                + " VALUES ('projection-owner', 'Projection owner', 'test-hash', 'STUDENT') RETURNING id",
            Long.class);
  }

  @Test
  void failedProfileUpdateRemainsPendingAndReplayAppliesItExactlyOnce() {
    ProfileRepository profileRepository = new ProfileRepository(jdbc, objectMapper);
    AtomicBoolean failFirstUpdate = new AtomicBoolean(true);
    PassiveProfileService profileService =
        new PassiveProfileService(profileRepository, objectMapper) {
          @Override
          public DimensionUpdateResult applyDimensionUpdate(
              Long userId, String dimensionKey, DimensionDelta delta) {
            if (failFirstUpdate.getAndSet(false)) {
              throw new DataAccessResourceFailureException("temporary profile database failure");
            }
            return super.applyDimensionUpdate(userId, dimensionKey, delta);
          }
        };
    ProfileDimensionProjectionOutboxRepository outboxRepository =
        new ProfileDimensionProjectionOutboxRepository(jdbc, objectMapper);
    ProfileDimensionProjectionProcessor processor =
        new ProfileDimensionProjectionProcessor(
            outboxRepository, profileService, transactionManager);
    ProfileDimensionProjectionDispatcher dispatcher =
        new ProfileDimensionProjectionDispatcher(outboxRepository, processor);
    ProfileDimensionProjection projection =
        new ProfileDimensionProjection(
            userId,
            "practical_ability",
            new DimensionDelta(
                0.02,
                1,
                List.of("practice"),
                Map.of("kcCode", "PY43-KC-001"),
                "PRACTICE_PASS"),
            "test-practice-event");
    long outboxId = outboxRepository.enqueue(projection);
    assertThat(outboxRepository.enqueue(projection)).isEqualTo(outboxId);
    long laterOutboxId =
        outboxRepository.enqueue(
            new ProfileDimensionProjection(
                userId,
                "practical_ability",
                new DimensionDelta(
                    0.01,
                    1,
                    List.of("later practice"),
                    Map.of("kcCode", "PY43-KC-002"),
                    "PRACTICE_PASS"),
                "test-later-practice-event"));
    assertThat(laterOutboxId).isGreaterThan(outboxId);

    dispatcher.dispatchOne(laterOutboxId);
    assertThat(outboxStatus(laterOutboxId)).isEqualTo("PENDING");
    assertThat(profileCount()).isZero();

    dispatcher.dispatchOne(outboxId);

    assertThat(outboxStatus(outboxId)).isEqualTo("PENDING");
    assertThat(outboxAttempts(outboxId)).isEqualTo(1);
    assertThat(profileCount()).isZero();
    dispatcher.dispatchOne(laterOutboxId);
    assertThat(outboxStatus(laterOutboxId)).isEqualTo("PENDING");

    jdbc.update(
        "UPDATE learner_profile_projection_outbox SET next_attempt_at = now() WHERE id = ?",
        outboxId);
    dispatcher.dispatchOne(outboxId);
    dispatcher.dispatchOne(outboxId);
    dispatcher.dispatchOne(laterOutboxId);
    dispatcher.dispatchOne(laterOutboxId);

    assertThat(outboxStatus(outboxId)).isEqualTo("APPLIED");
    assertThat(outboxStatus(laterOutboxId)).isEqualTo("APPLIED");
    assertThat(jdbc.queryForObject(
            "SELECT dimensions #>> '{practical_ability,evidence_count}'"
                + " FROM learner_profile WHERE user_id = ?",
            String.class,
            userId))
        .isEqualTo("2");
  }

  private String outboxStatus(long id) {
    return jdbc.queryForObject(
        "SELECT status FROM learner_profile_projection_outbox WHERE id = ?", String.class, id);
  }

  private int outboxAttempts(long id) {
    return jdbc.queryForObject(
        "SELECT attempt_count FROM learner_profile_projection_outbox WHERE id = ?",
        Integer.class,
        id);
  }

  private int profileCount() {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM learner_profile WHERE user_id = ?", Integer.class, userId);
  }
}
