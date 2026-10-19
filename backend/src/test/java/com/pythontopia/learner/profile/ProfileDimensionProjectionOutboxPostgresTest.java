package com.pythontopia.learner.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.learner.profile.dto.DimensionDelta;
import com.pythontopia.learner.profile.dto.DimensionUpdateResult;
import com.pythontopia.learner.profile.dto.ProfileDimensionProjectionOutboxDto;
import com.pythontopia.learner.profile.repository.ProfileRepository;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.springframework.context.ApplicationEventPublisher;
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
        .target(MigrationVersion.fromVersion("226"))
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

  @Test
  void repeatedFailuresBackOffAndQuarantineWithoutSkippingLaterUpdatesForTheUser() {
    ProfileDimensionProjectionOutboxRepository outboxRepository =
        new ProfileDimensionProjectionOutboxRepository(jdbc, objectMapper);
    PassiveProfileService alwaysFailingProfileService =
        new PassiveProfileService(new ProfileRepository(jdbc, objectMapper), objectMapper) {
          @Override
          public DimensionUpdateResult applyDimensionUpdate(
              Long userId, String dimensionKey, DimensionDelta delta) {
            throw new DataAccessResourceFailureException("private profile payload");
          }
        };
    ProfileDimensionProjectionProcessor processor =
        new ProfileDimensionProjectionProcessor(
            outboxRepository, alwaysFailingProfileService, transactionManager);
    ProfileDimensionProjectionDispatcher dispatcher =
        new ProfileDimensionProjectionDispatcher(outboxRepository, processor);
    long firstId =
        outboxRepository.enqueue(
            new ProfileDimensionProjection(
                userId,
                "practical_ability",
                new DimensionDelta(0.02, 1, List.of("first"), Map.of(), "PRACTICE_PASS"),
                "failed-first"));
    long laterId =
        outboxRepository.enqueue(
            new ProfileDimensionProjection(
                userId,
                "practical_ability",
                new DimensionDelta(0.01, 1, List.of("later"), Map.of(), "PRACTICE_PASS"),
                "blocked-later"));

    dispatcher.dispatchOne(firstId);
    assertThat(secondsUntilNextAttempt(firstId)).isBetween(4L, 5L);
    dispatcher.dispatchOne(laterId);
    assertThat(outboxStatus(laterId)).isEqualTo("PENDING");

    jdbc.update(
        "UPDATE learner_profile_projection_outbox SET next_attempt_at = now() WHERE id = ?", firstId);
    dispatcher.dispatchOne(firstId);
    assertThat(secondsUntilNextAttempt(firstId)).isBetween(9L, 10L);

    for (int attempt = 3; attempt <= 8; attempt++) {
      jdbc.update(
          "UPDATE learner_profile_projection_outbox SET next_attempt_at = now() WHERE id = ?",
          firstId);
      dispatcher.dispatchOne(firstId);
    }

    assertThat(outboxStatus(firstId)).isEqualTo("FAILED");
    assertThat(outboxAttempts(firstId)).isEqualTo(8);
    assertThat(jdbc.queryForObject(
            "SELECT failed_at IS NOT NULL FROM learner_profile_projection_outbox WHERE id = ?",
            Boolean.class,
            firstId))
        .isTrue();
    assertThat(jdbc.queryForObject(
            "SELECT error_kind FROM learner_profile_projection_outbox WHERE id = ?",
            String.class,
            firstId))
        .isEqualTo("DataAccessResourceFailureException");
    dispatcher.dispatchOne(laterId);
    assertThat(outboxStatus(laterId)).isEqualTo("PENDING");
    assertThat(profileCount()).isZero();
  }

  @Test
  void failedHeadCanBeValidatedRepairedAndReplayedBeforeLaterUpdates() {
    ProfileDimensionProjectionOutboxRepository outboxRepository =
        new ProfileDimensionProjectionOutboxRepository(jdbc, objectMapper);
    long firstId =
        outboxRepository.enqueue(
            new ProfileDimensionProjection(
                userId,
                "practical_ability",
                new DimensionDelta(0.02, 1, List.of("bad"), Map.of(), "PRACTICE_PASS"),
                "repair-first"));
    long laterId =
        outboxRepository.enqueue(
            new ProfileDimensionProjection(
                userId,
                "practical_ability",
                new DimensionDelta(0.01, 1, List.of("later"), Map.of(), "PRACTICE_PASS"),
                "repair-later"));
    jdbc.update(
        "UPDATE learner_profile_projection_outbox"
            + " SET delta = '{\"scoreDelta\":\"invalid\",\"source\":\"PRACTICE_PASS\"}'::jsonb,"
            + " attempt_count = 7 WHERE id = ?",
        firstId);

    ProfileDimensionProjectionProcessor failingProcessor =
        new ProfileDimensionProjectionProcessor(
            outboxRepository,
            new PassiveProfileService(new ProfileRepository(jdbc, objectMapper), objectMapper),
            transactionManager);
    ProfileDimensionProjectionDispatcher failingDispatcher =
        new ProfileDimensionProjectionDispatcher(outboxRepository, failingProcessor);
    failingDispatcher.dispatchOne(firstId);
    assertThat(outboxStatus(firstId)).isEqualTo("FAILED");
    failingDispatcher.dispatchOne(laterId);
    assertThat(outboxStatus(laterId)).isEqualTo("PENDING");

    ProfileDimensionProjectionOutboxService outboxService =
        new ProfileDimensionProjectionOutboxService(outboxRepository, ignored -> {});
    DimensionDelta repairedDelta =
        new DimensionDelta(0.02, 1, List.of("repaired"), Map.of(), "PRACTICE_PASS");
    assertThatThrownBy(
            () -> outboxService.repairAndReplay(firstId, 900L, "unknown_dimension", repairedDelta))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(outboxStatus(firstId)).isEqualTo("FAILED");

    assertThat(
            outboxService.repairAndReplay(firstId, 900L, "practical_ability", repairedDelta))
        .isTrue();
    assertThat(outboxStatus(firstId)).isEqualTo("PENDING");
    assertThat(jdbc.queryForObject(
            "SELECT failed_at IS NULL FROM learner_profile_projection_outbox WHERE id = ?",
            Boolean.class,
            firstId))
        .isTrue();
    assertThat(outboxAttempts(firstId)).isZero();
    assertThat(jdbc.queryForObject(
            "SELECT manual_replay_count FROM learner_profile_projection_outbox WHERE id = ?",
            Integer.class,
            firstId))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject(
            "SELECT last_replayed_by FROM learner_profile_projection_outbox WHERE id = ?",
            Long.class,
            firstId))
        .isEqualTo(900L);
    assertThat(jdbc.queryForObject(
            "SELECT last_replayed_at IS NOT NULL FROM learner_profile_projection_outbox WHERE id = ?",
            Boolean.class,
            firstId))
        .isTrue();

    ProfileDimensionProjectionProcessor healthyProcessor =
        new ProfileDimensionProjectionProcessor(
            outboxRepository,
            new PassiveProfileService(new ProfileRepository(jdbc, objectMapper), objectMapper),
            transactionManager);
    assertThat(healthyProcessor.process(firstId)).isTrue();
    assertThat(healthyProcessor.process(laterId)).isTrue();
    assertThat(jdbc.queryForObject(
            "SELECT dimensions #>> '{practical_ability,evidence_count}'"
                + " FROM learner_profile WHERE user_id = ?",
            String.class,
            userId))
        .isEqualTo("2");
    assertThat(jdbc.queryForObject(
            "SELECT dimensions #>> '{practical_ability,tags}'"
                + " FROM learner_profile WHERE user_id = ?",
            String.class,
            userId))
        .contains("repaired", "later");
    assertThat(outboxRepository.findForAdmin(firstId))
        .get()
        .satisfies(entry -> {
          assertThat(entry.status()).isEqualTo("APPLIED");
          assertThat(entry.manualReplayCount()).isEqualTo(1);
          assertThat(entry.lastReplayedBy()).isEqualTo(900L);
          assertThat(entry.lastReplayedAt()).isNotNull();
        });
  }

  @Test
  void adminLookupPagesOldestFailuresAndShowsPersistedReplayAttribution() {
    ProfileDimensionProjectionOutboxRepository repository =
        new ProfileDimensionProjectionOutboxRepository(jdbc, objectMapper);
    long oldestId = insertFailedOutbox("admin-list-oldest", "now() - interval '2 minutes'");
    long newestId = insertFailedOutbox("admin-list-newest", "now() - interval '1 minute'");
    ProfileDimensionProjectionOutboxService service =
        new ProfileDimensionProjectionOutboxService(repository, ignored -> {});

    var firstPage = service.pageFailed(0, 1);
    var secondPage = service.pageFailed(1, 1);

    assertThat(firstPage.content()).extracting(ProfileDimensionProjectionOutboxDto::id)
        .containsExactly(oldestId);
    assertThat(firstPage.totalElements()).isEqualTo(2);
    assertThat(firstPage.totalPages()).isEqualTo(2);
    assertThat(secondPage.content()).extracting(ProfileDimensionProjectionOutboxDto::id)
        .containsExactly(newestId);
    assertThat(service.findForAdmin(oldestId)).get().satisfies(entry -> {
      assertThat(entry.status()).isEqualTo("FAILED");
      assertThat(entry.errorKind()).isEqualTo("IllegalStateException");
      assertThat(entry.lastReplayedBy()).isNull();
    });
  }

  @Test
  void blockedRowsDoNotFillThePollBatchAheadOfAnotherUsersWork() {
    ProfileDimensionProjectionOutboxRepository outboxRepository =
        new ProfileDimensionProjectionOutboxRepository(jdbc, objectMapper);
    jdbc.update(
        "INSERT INTO learner_profile_projection_outbox"
            + " (user_id, idempotency_key, dimension_key, delta, status, attempt_count,"
            + " error_kind, failed_at)"
            + " VALUES (?, 'blocked-head', 'practical_ability',"
            + " '{\"scoreDelta\":0.02,\"evidenceIncrement\":1,\"source\":\"PRACTICE_PASS\"}'::jsonb,"
            + " 'FAILED', 8, 'IllegalStateException', now())",
        userId);
    jdbc.update(
        "INSERT INTO learner_profile_projection_outbox"
            + " (user_id, idempotency_key, dimension_key, delta)"
            + " SELECT ?, 'blocked-tail-' || n, 'practical_ability',"
            + " '{\"scoreDelta\":0.01,\"evidenceIncrement\":1,\"source\":\"PRACTICE_PASS\"}'::jsonb"
            + " FROM generate_series(1, 100) n",
        userId);
    long otherUserId =
        jdbc.queryForObject(
            "INSERT INTO users(username, display_name, password_hash, role)"
                + " VALUES ('projection-other', 'Projection other', 'test-hash', 'STUDENT') RETURNING id",
            Long.class);
    long otherUserOutboxId =
        outboxRepository.enqueue(
            new ProfileDimensionProjection(
                otherUserId,
                "practical_ability",
                new DimensionDelta(0.01, 1, List.of("other-user"), Map.of(), "PRACTICE_PASS"),
                "other-user-work"));

    assertThat(outboxRepository.findDuePendingIds(100)).containsExactly(otherUserOutboxId);

    ProfileDimensionProjectionDispatcher dispatcher =
        new ProfileDimensionProjectionDispatcher(
            outboxRepository,
            new ProfileDimensionProjectionProcessor(
                outboxRepository,
                new PassiveProfileService(new ProfileRepository(jdbc, objectMapper), objectMapper),
                transactionManager));
    dispatcher.retryPending();

    assertThat(outboxStatus(otherUserOutboxId)).isEqualTo("APPLIED");
    assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM learner_profile_projection_outbox"
                + " WHERE user_id = ? AND status = 'PENDING'",
            Integer.class,
            userId))
        .isEqualTo(100);
  }

  @Test
  void concurrentWorkersApplyOneProjectionOnce() throws Exception {
    ProfileDimensionProjectionOutboxRepository outboxRepository =
        new ProfileDimensionProjectionOutboxRepository(jdbc, objectMapper);
    long outboxId =
        outboxRepository.enqueue(
            new ProfileDimensionProjection(
                userId,
                "practical_ability",
                new DimensionDelta(0.02, 1, List.of("once"), Map.of(), "PRACTICE_PASS"),
                "concurrent-projection"));
    CountDownLatch firstEnteredProfileUpdate = new CountDownLatch(1);
    CountDownLatch releaseFirstWorker = new CountDownLatch(1);
    CountDownLatch secondWorkerStarted = new CountDownLatch(1);
    AtomicInteger profileApplications = new AtomicInteger();
    PassiveProfileService blockingProfileService =
        new PassiveProfileService(new ProfileRepository(jdbc, objectMapper), objectMapper) {
          @Override
          public DimensionUpdateResult applyDimensionUpdate(
              Long userId, String dimensionKey, DimensionDelta delta) {
            profileApplications.incrementAndGet();
            firstEnteredProfileUpdate.countDown();
            try {
              if (!releaseFirstWorker.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test worker release timed out");
              }
            } catch (InterruptedException ex) {
              Thread.currentThread().interrupt();
              throw new IllegalStateException("test worker interrupted", ex);
            }
            return super.applyDimensionUpdate(userId, dimensionKey, delta);
          }
        };
    ProfileDimensionProjectionProcessor processor =
        new ProfileDimensionProjectionProcessor(
            outboxRepository, blockingProfileService, transactionManager);

    try (var workers = Executors.newFixedThreadPool(2)) {
      var first = workers.submit(() -> processor.process(outboxId));
      assertThat(firstEnteredProfileUpdate.await(10, TimeUnit.SECONDS)).isTrue();
      var second =
          workers.submit(
              () -> {
                secondWorkerStarted.countDown();
                return processor.process(outboxId);
              });
      assertThat(secondWorkerStarted.await(10, TimeUnit.SECONDS)).isTrue();
      releaseFirstWorker.countDown();

      assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
      assertThat(second.get(10, TimeUnit.SECONDS)).isFalse();
    } finally {
      releaseFirstWorker.countDown();
    }

    assertThat(profileApplications.get()).isEqualTo(1);
    assertThat(outboxStatus(outboxId)).isEqualTo("APPLIED");
    assertThat(jdbc.queryForObject(
            "SELECT dimensions #>> '{practical_ability,evidence_count}'"
                + " FROM learner_profile WHERE user_id = ?",
            String.class,
            userId))
        .isEqualTo("1");
  }

  @Test
  void failedTransactionLeavesProfileAndRevisionUntouchedForFreshWorkerRecovery() {
    ProfileDimensionProjectionOutboxRepository outboxRepository =
        new ProfileDimensionProjectionOutboxRepository(jdbc, objectMapper);
    long outboxId =
        outboxRepository.enqueue(
            new ProfileDimensionProjection(
                userId,
                "practical_ability",
                new DimensionDelta(0.02, 1, List.of("recovered"), Map.of(), "PRACTICE_PASS"),
                "restart-recovery"));
    AtomicBoolean failAfterProfileAndAuditWrite = new AtomicBoolean(true);
    PassiveProfileService failingProfileService =
        new PassiveProfileService(new ProfileRepository(jdbc, objectMapper), objectMapper) {
          @Override
          public DimensionUpdateResult applyDimensionUpdate(
              Long userId, String dimensionKey, DimensionDelta delta) {
            DimensionUpdateResult result = super.applyDimensionUpdate(userId, dimensionKey, delta);
            if (failAfterProfileAndAuditWrite.getAndSet(false)) {
              throw new DataAccessResourceFailureException("connection lost before commit");
            }
            return result;
          }
        };
    ProfileDimensionProjectionDispatcher firstProcess =
        new ProfileDimensionProjectionDispatcher(
            outboxRepository,
            new ProfileDimensionProjectionProcessor(
                outboxRepository, failingProfileService, transactionManager));

    firstProcess.dispatchOne(outboxId);
    assertThat(outboxStatus(outboxId)).isEqualTo("PENDING");
    assertThat(profileCount()).isZero();
    assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM profile_revision WHERE user_id = ?", Integer.class, userId))
        .isZero();

    jdbc.update(
        "UPDATE learner_profile_projection_outbox SET next_attempt_at = now() WHERE id = ?",
        outboxId);
    ProfileDimensionProjectionDispatcher restartedProcess =
        new ProfileDimensionProjectionDispatcher(
            new ProfileDimensionProjectionOutboxRepository(jdbc, objectMapper),
            new ProfileDimensionProjectionProcessor(
                new ProfileDimensionProjectionOutboxRepository(jdbc, objectMapper),
                new PassiveProfileService(new ProfileRepository(jdbc, objectMapper), objectMapper),
                transactionManager));
    restartedProcess.retryPending();

    assertThat(outboxStatus(outboxId)).isEqualTo("APPLIED");
    assertThat(profileCount()).isEqualTo(1);
    assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM profile_revision WHERE user_id = ?", Integer.class, userId))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject(
            "SELECT dimensions #>> '{practical_ability,evidence_count}'"
                + " FROM learner_profile WHERE user_id = ?",
            String.class,
            userId))
        .isEqualTo("1");
  }

  @Test
  void practiceAndOperationCheckForOneSubmissionApplyOnePracticalProjection() {
    ProfileDimensionProjectionOutboxRepository outboxRepository =
        new ProfileDimensionProjectionOutboxRepository(jdbc, objectMapper);
    ApplicationEventPublisher eventPublisher = ignored -> {};
    PassiveProfileRevisionListener listener =
        new PassiveProfileRevisionListener(
            new ProfileDimensionProjectionOutboxService(outboxRepository, eventPublisher));
    listener.onPracticeSucceeded(
        new LearningEvent.PracticeSucceeded(userId, "PY43-KC-001", 101L, "medium", 501L));
    listener.onResourceCheckCompleted(
        new LearningEvent.ResourceCheckCompleted(
            userId,
            31L,
            99L,
            "PY43-KC-001",
            "OPERATION",
            true,
            new java.math.BigDecimal("0.05"),
            null,
            501L));

    assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM learner_profile_projection_outbox"
                + " WHERE idempotency_key = ? AND dimension_key = 'practical_ability'",
            Integer.class,
            "submission:" + userId + ":501"))
        .isEqualTo(1);

    PassiveProfileService profileService =
        new PassiveProfileService(new ProfileRepository(jdbc, objectMapper), objectMapper);
    ProfileDimensionProjectionProcessor processor =
        new ProfileDimensionProjectionProcessor(
            outboxRepository, profileService, transactionManager);
    ProfileDimensionProjectionDispatcher dispatcher =
        new ProfileDimensionProjectionDispatcher(outboxRepository, processor);
    dispatcher.dispatchOne(outboxRepository.findDuePendingIds(10).getFirst());

    assertThat(jdbc.queryForObject(
            "SELECT dimensions #>> '{practical_ability,evidence_count}'"
                + " FROM learner_profile WHERE user_id = ?",
            String.class,
            userId))
        .isEqualTo("1");
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

  private long secondsUntilNextAttempt(long id) {
    return jdbc.queryForObject(
        "SELECT ceil(extract(epoch FROM (next_attempt_at - now())))::bigint"
            + " FROM learner_profile_projection_outbox WHERE id = ?",
        Long.class,
        id);
  }

  private int profileCount() {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM learner_profile WHERE user_id = ?", Integer.class, userId);
  }

  private long insertFailedOutbox(String idempotencyKey, String failedAtExpression) {
    return jdbc.queryForObject(
        "INSERT INTO learner_profile_projection_outbox"
            + " (user_id, idempotency_key, dimension_key, delta, status, attempt_count,"
            + " error_kind, failed_at)"
            + " VALUES (?, ?, 'practical_ability',"
            + " '{\"scoreDelta\":0.02,\"evidenceIncrement\":1,\"source\":\"PRACTICE_PASS\"}'::jsonb,"
            + " 'FAILED', 8, 'IllegalStateException', "
            + failedAtExpression
            + ") RETURNING id",
        Long.class,
        userId,
        idempotencyKey);
  }
}
