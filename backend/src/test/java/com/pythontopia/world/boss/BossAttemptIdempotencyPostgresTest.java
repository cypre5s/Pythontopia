package com.pythontopia.world.boss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.world.boss.dto.BossAttemptRequest;
import com.pythontopia.world.boss.dto.BossAttemptResultDto;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class BossAttemptIdempotencyPostgresTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:pg16")
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("pythontopia")
          .withUsername("pytopia")
          .withPassword("pytopia");

  private static JdbcTemplate jdbc;
  private static TransactionTemplate transactions;

  @BeforeAll
  static void migrate() {
    DriverManagerDataSource source = new DriverManagerDataSource();
    source.setDriverClassName("org.postgresql.Driver");
    source.setUrl(POSTGRES.getJdbcUrl());
    source.setUsername(POSTGRES.getUsername());
    source.setPassword(POSTGRES.getPassword());
    Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
    jdbc = new JdbcTemplate(source);
    transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
  }

  @Test
  void replayReturnsStoredVerdictAndPublishesTheLearningEventOnlyOnce() {
    long userId = createStudent();
    UUID attemptId = UUID.randomUUID();
    BossAttemptRequest request = new BossAttemptRequest("p1", 1, 1, "print(1)", 3, attemptId);
    BossAttemptResultDto result = new BossAttemptResultDto("p1", true, 2, 2, null, 100, "PASS");
    LearningEvent event = new LearningEvent.PracticeSucceeded(userId, "PY43-KC-001", null, "easy");
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    BossAttemptIdempotencyService service =
        new BossAttemptIdempotencyService(jdbc, new ObjectMapper(), events);
    BossAttemptIdempotencyService.Claim claim = service.claim(userId, attemptId, request);

    transactions.execute(status -> service.complete(userId, attemptId, claim, result, event));
    var replay = service.claim(userId, attemptId, request);

    assertThat(replay.replay()).isEqualTo(result);
    verify(events).publishEvent(event);
  }

  @Test
  void requestIdCannotBeReusedForDifferentCodeAndConcurrentReplayFailsClosed() {
    long userId = createStudent();
    UUID attemptId = UUID.randomUUID();
    BossAttemptRequest request = new BossAttemptRequest("p1", 1, 1, "print(1)", 3, attemptId);
    BossAttemptIdempotencyService service =
        new BossAttemptIdempotencyService(
            jdbc, new ObjectMapper(), mock(ApplicationEventPublisher.class));
    service.claim(userId, attemptId, request);

    assertThatThrownBy(
            () ->
                service.claim(
                    userId,
                    attemptId,
                    new BossAttemptRequest("p1", 1, 1, "print(2)", 3, attemptId)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("不同提交内容");
    assertThatThrownBy(() -> service.claim(userId, attemptId, request))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("处理中");
  }

  @Test
  void staleClaimCanBeRecoveredAfterAWorkerCrash() {
    long userId = createStudent();
    UUID attemptId = UUID.randomUUID();
    BossAttemptRequest request = new BossAttemptRequest("p1", 1, 1, "print(1)", 3, attemptId);
    BossAttemptIdempotencyService service =
        new BossAttemptIdempotencyService(
            jdbc, new ObjectMapper(), mock(ApplicationEventPublisher.class));
    var firstClaim = service.claim(userId, attemptId, request);
    jdbc.update(
        "UPDATE boss_attempt_idempotency SET updated_at = now() - interval '5 minutes'"
            + " WHERE user_id = ? AND attempt_id = ?",
        userId,
        attemptId);

    var recoveredClaim = service.claim(userId, attemptId, request);

    assertThat(recoveredClaim.token()).isNotEqualTo(firstClaim.token());
  }

  @Test
  void claimIsNotRecoveredWhileBossGraphRequestMayStillBeRunning() {
    long userId = createStudent();
    UUID attemptId = UUID.randomUUID();
    BossAttemptRequest request = new BossAttemptRequest("p1", 1, 1, "print(1)", 3, attemptId);
    BossAttemptIdempotencyService service =
        new BossAttemptIdempotencyService(
            jdbc, new ObjectMapper(), mock(ApplicationEventPublisher.class));
    service.claim(userId, attemptId, request);
    jdbc.update(
        "UPDATE boss_attempt_idempotency SET updated_at = now() - interval '3 minutes'"
            + " WHERE user_id = ? AND attempt_id = ?",
        userId,
        attemptId);

    assertThatThrownBy(() -> service.claim(userId, attemptId, request))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("处理中");
  }

  @Test
  void differentAttemptIdsAreThrottledPerUserBeforeAnotherJudgeCanRun() {
    long userId = createStudent();
    BossAttemptIdempotencyService service =
        new BossAttemptIdempotencyService(
            jdbc, new ObjectMapper(), mock(ApplicationEventPublisher.class));
    BossAttemptRequest first =
        new BossAttemptRequest("p1", 1, 1, "print(1)", 3, UUID.randomUUID());
    BossAttemptRequest second =
        new BossAttemptRequest("p1", 1, 1, "print(2)", 3, UUID.randomUUID());
    transactions.execute(status -> service.claim(userId, first.attemptId(), first));

    assertThatThrownBy(
            () ->
                transactions.execute(status -> service.claim(userId, second.attemptId(), second)))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).code())
        .isEqualTo("BOSS_ATTEMPT_RATE_LIMITED");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM boss_attempt_idempotency WHERE user_id = ?",
                Integer.class,
                userId))
        .isEqualTo(1);
  }

  private long createStudent() {
    return jdbc.queryForObject(
        "INSERT INTO users (username, display_name, password_hash, role)"
            + " VALUES (?, 'Student', 'x', 'STUDENT') RETURNING id",
        Long.class,
        "boss-idempotency-" + UUID.randomUUID());
  }
}
