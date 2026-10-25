package com.pythontopia.tutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.submission.repository.PublicProblemRepository;
import com.pythontopia.tutor.dto.TutorWorkflowCardCursorDto;
import com.pythontopia.tutor.dto.TutorWorkflowSessionDto;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class TutorWorkflowRepositoryIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("pythontopia")
          .withUsername("pytopia")
          .withPassword("pytopia");

  private static JdbcTemplate jdbc;
  private static TutorWorkflowRepository repository;
  private static TutorWorkflowAttachmentRepository attachmentRepository;
  private static Long legacyTerminalCheckpointSessionId;
  private Long userId;
  private TutorWorkflowSessionDto session;

  @BeforeAll
  static void migrateAndWire() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.postgresql.Driver");
    dataSource.setUrl(POSTGRES.getJdbcUrl());
    dataSource.setUsername(POSTGRES.getUsername());
    dataSource.setPassword(POSTGRES.getPassword());
    jdbc = new JdbcTemplate(dataSource);
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .target(MigrationVersion.fromVersion("202"))
        .load()
        .migrate();
    seedLegacyTerminalCheckpoints();
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

    var transactionManager = new DataSourceTransactionManager(dataSource);
    attachmentRepository = new TutorWorkflowAttachmentRepository(jdbc);
    var proxyFactory = new ProxyFactory(new TutorWorkflowRepository(jdbc, new ObjectMapper()));
    proxyFactory.addAdvice(
        new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
    repository = (TutorWorkflowRepository) proxyFactory.getProxy();
  }

  private static void seedLegacyTerminalCheckpoints() {
    Long legacyUserId =
        jdbc.queryForObject(
            "INSERT INTO users (username, display_name, password_hash, role)"
                + " VALUES ('tutor-checkpoint-migration', 'Migration Test', 'hash', 'STUDENT')"
                + " RETURNING id",
            Long.class);
    legacyTerminalCheckpointSessionId =
        jdbc.queryForObject(
            "INSERT INTO tutor_workflow_session (user_id, problem_id, session_key)"
                + " VALUES (?, 476, 'wf-checkpoint-migration') RETURNING id",
            Long.class,
            legacyUserId);
    jdbc.update(
        "INSERT INTO tutor_workflow_autogen_checkpoint"
            + " (session_id, run_id, request_fingerprint, checkpoint, status)"
            + " VALUES (?, ?::uuid, ?, '{\"student_text\":\"sensitive\"}'::jsonb, 'COMPLETED'),"
            + " (?, ?::uuid, ?, '{\"student_text\":\"sensitive\"}'::jsonb, 'FAILED')",
        legacyTerminalCheckpointSessionId,
        UUID.randomUUID(),
        "d".repeat(64),
        legacyTerminalCheckpointSessionId,
        UUID.randomUUID(),
        "e".repeat(64));
  }

  @BeforeEach
  void createSession() {
    userId =
        jdbc.queryForObject(
            "INSERT INTO users (username, display_name, password_hash, role)"
                + " VALUES (?, 'Tutor Workflow Test', 'hash', 'STUDENT') RETURNING id",
            Long.class,
            "tutor-workflow-" + System.nanoTime());
    session =
        repository.insertSession(
            userId, 476L, "wf-" + userId + "-476-" + UUID.randomUUID().toString().substring(0, 8));
  }

  @Test
  void runProjectionCommitsOnceForRunId() throws Exception {
    UUID runId = UUID.randomUUID();
    TutorWorkflowRepository.RunProjection projection =
        projection(runId, "CODING", 2, "PLAN_START", "plan_card", "{\"content\":\"started\"}");

    assertThat(repository.persistRunProjection(session.id(), projection)).isTrue();
    assertThat(repository.persistRunProjection(session.id(), projection)).isFalse();

    assertThat(
            jdbc.queryForObject(
                "SELECT phase FROM tutor_workflow_session WHERE id = ?",
                String.class,
                session.id()))
        .isEqualTo("CODING");
    assertThat(
            jdbc.queryForObject(
                "SELECT seq FROM tutor_workflow_session WHERE id = ?", Integer.class, session.id()))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM tutor_workflow_run_projection WHERE session_id = ?",
                Integer.class,
                session.id()))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM tutor_workflow_card WHERE session_id = ?",
                Integer.class,
                session.id()))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM tutor_workflow_action_event WHERE session_id = ?",
                Integer.class,
                session.id()))
        .isEqualTo(1);
    assertThat(
            new ObjectMapper()
                .readTree(repository.findAutoGenRunResult(session.id(), runId).orElseThrow()))
        .isEqualTo(
            new ObjectMapper()
                .readTree(
                    "{\"session_id\":\"wf-test\",\"event\":\"PLAN_START\","
                        + "\"phase\":\"CODING\",\"card_key\":\"plan_card\","
                        + "\"card\":{\"content\":\"started\"},"
                        + "\"available_actions\":[],\"seq\":2}"));
  }

  @Test
  void cardHistoryUsesStableCursorAndReturnsChronologicalPages() {
    repository.insertCard(session.id(), 1, "CHAT", "ai_reply", "{\"content\":\"one\"}");
    repository.insertCard(session.id(), 2, "CHAT", "ai_reply", "{\"content\":\"two-a\"}");
    repository.insertCard(session.id(), 2, "CHAT", "ai_reply", "{\"content\":\"two-b\"}");
    repository.insertCard(session.id(), 3, "CHAT", "ai_reply", "{\"content\":\"three\"}");

    var latest = repository.findLatestCardPage(session.id(), 2);

    assertThat(latest.cards())
        .extracting(card -> card.payload().get("content").asText())
        .containsExactly("two-b", "three");
    var cursor = latest.nextCursor();
    assertThat(cursor)
        .isEqualTo(
            new TutorWorkflowCardCursorDto(
                2,
                jdbc.queryForObject(
                    "SELECT id FROM tutor_workflow_card WHERE session_id = ?"
                        + " AND payload->>'content' = 'two-b'",
                    Long.class,
                    session.id())));

    var earlier = repository.findCardsBefore(session.id(), cursor, 2);

    assertThat(earlier.cards())
        .extracting(card -> card.payload().get("content").asText())
        .containsExactly("one", "two-a");
    assertThat(earlier.nextCursor()).isNull();
  }

  @Test
  void deleteExpiredActionEventsKeepsRecentSessionCardsAndEvents() {
    repository.insertCard(session.id(), 1, "CHAT", "ai_reply", "{\"content\":\"kept\"}");
    repository.insertActionEvent(session.id(), 1, "ACTION_CLICKED", "old", "{}");
    repository.insertActionEvent(session.id(), 2, "ACTION_CLICKED", "recent", "{}");
    jdbc.update(
        "UPDATE tutor_workflow_action_event SET created_at = now() - interval '91 days'"
            + " WHERE session_id = ? AND seq = 1",
        session.id());

    int deleted =
        repository.deleteActionEventsCreatedBefore(
            OffsetDateTime.now(ZoneOffset.UTC).minusDays(90));

    assertThat(deleted).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM tutor_workflow_action_event WHERE session_id = ?",
                Integer.class,
                session.id()))
        .isEqualTo(1);
    assertThat(repository.findRecentCards(session.id(), 10)).hasSize(1);
  }

  @Test
  void migrationClearsExistingTerminalCheckpointPayloads() {
    assertThat(
            jdbc.queryForList(
                "SELECT checkpoint::text FROM tutor_workflow_autogen_checkpoint"
                    + " WHERE session_id = ? ORDER BY status",
                String.class,
                legacyTerminalCheckpointSessionId))
        .containsExactly("{}", "{}");
    assertThat(
            jdbc.queryForList(
                "SELECT status FROM tutor_workflow_autogen_checkpoint"
                    + " WHERE session_id = ? ORDER BY status",
                String.class,
                legacyTerminalCheckpointSessionId))
        .containsExactly("COMPLETED", "FAILED");
  }

  @Test
  void attachmentRepositoryPersistsExpiryForPostgres() {
    UUID attachmentId = UUID.randomUUID();
    Instant expiresAt = Instant.now().plusSeconds(60);

    attachmentRepository.insert(session.id(), attachmentId, "image/png", new byte[] {1}, expiresAt);

    assertThat(
            jdbc.queryForObject(
                "SELECT expires_at > now() FROM tutor_workflow_attachment WHERE id = ?",
                Boolean.class,
                attachmentId))
        .isTrue();
  }

  @Test
  void autogenCheckpointClaimResumeAndCompletionAreIdempotent() {
    UUID firstRunId = UUID.randomUUID();
    String fingerprint = "a".repeat(64);
    var claimed = repository.claimAutoGenRun(session.id(), firstRunId, fingerprint).orElseThrow();

    assertThat(claimed.runId()).isEqualTo(firstRunId);
    assertThat(claimed.stageIndex()).isEqualTo(-1);
    assertThat(repository.claimAutoGenRun(session.id(), UUID.randomUUID(), fingerprint)).isEmpty();

    repository.saveAutoGenCheckpoint(
        session.id(),
        firstRunId,
        fingerprint,
        1,
        "generate_card",
        "{\"team_state\":{\"agents\":[]},\"workflow_state\":\"state\"}");
    repository.transitionAutoGenRun(session.id(), firstRunId, "INTERRUPTED");

    var resumed =
        repository.claimAutoGenRun(session.id(), UUID.randomUUID(), fingerprint).orElseThrow();
    assertThat(resumed.runId()).isEqualTo(firstRunId);
    assertThat(resumed.stageIndex()).isEqualTo(1);
    assertThat(resumed.stageName()).isEqualTo("generate_card");
    assertThat(resumed.checkpointJson()).contains("team_state");

    repository.persistRunProjection(
        session.id(), projection(firstRunId, "READING", 1, "READING", null, null));
    var completed = repository.findAutoGenCheckpoint(session.id(), firstRunId).orElseThrow();
    assertThat(completed.status()).isEqualTo("COMPLETED");
    assertThat(completed.checkpointJson()).isEqualTo("{}");

    var nextRun =
        repository.claimAutoGenRun(session.id(), UUID.randomUUID(), fingerprint).orElseThrow();
    assertThat(nextRun.runId()).isNotEqualTo(firstRunId);
  }

  @Test
  void autogenCheckpointRejectsRegressiveStageUpdates() {
    UUID runId = UUID.randomUUID();
    String fingerprint = "b".repeat(64);
    repository.claimAutoGenRun(session.id(), runId, fingerprint).orElseThrow();
    repository.saveAutoGenCheckpoint(session.id(), runId, fingerprint, 2, "generate_card", "{}");

    assertThatThrownBy(
            () ->
                repository.saveAutoGenCheckpoint(
                    session.id(), runId, fingerprint, 1, "coach_decide", "{}"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("stage");
  }

  @Test
  void failedAutoGenRunClearsCheckpointPayload() {
    UUID runId = UUID.randomUUID();
    String fingerprint = "c".repeat(64);
    repository.claimAutoGenRun(session.id(), runId, fingerprint).orElseThrow();
    repository.saveAutoGenCheckpoint(
        session.id(), runId, fingerprint, 1, "generate_card", "{\"team_state\":{}}");

    assertThat(repository.transitionAutoGenRun(session.id(), runId, "FAILED")).isTrue();

    var failed = repository.findAutoGenCheckpoint(session.id(), runId).orElseThrow();
    assertThat(failed.status()).isEqualTo("FAILED");
    assertThat(failed.checkpointJson()).isEqualTo("{}");
  }

  @Test
  void failedAutoGenRunCanBeClaimedAgainWithItsStableRequestId() {
    UUID requestId = UUID.randomUUID();
    String fingerprint = "9".repeat(64);
    repository.claimAutoGenRun(session.id(), requestId, fingerprint).orElseThrow();
    assertThat(repository.transitionAutoGenRun(session.id(), requestId, "FAILED")).isTrue();

    assertThat(repository.retryFailedAutoGenRun(session.id(), requestId, fingerprint)).isTrue();
    var retry = repository.claimAutoGenRun(session.id(), requestId, fingerprint).orElseThrow();

    assertThat(retry.runId()).isEqualTo(requestId);
    assertThat(retry.status()).isEqualTo("RUNNING");
    assertThat(retry.stageIndex()).isEqualTo(-1);
  }

  @Test
  void failedCardInsertRollsBackRunMarkerProgressStateAndEvent() {
    UUID runId = UUID.randomUUID();
    TutorWorkflowRepository.RunProjection projection =
        projection(runId, "CODING", 3, "PLAN_RESPONSE", "ai_reply", "not-json");

    assertThatThrownBy(() -> repository.persistRunProjection(session.id(), projection))
        .isInstanceOf(RuntimeException.class);

    assertThat(
            jdbc.queryForObject(
                "SELECT phase FROM tutor_workflow_session WHERE id = ?",
                String.class,
                session.id()))
        .isEqualTo("READING");
    assertThat(
            jdbc.queryForObject(
                "SELECT seq FROM tutor_workflow_session WHERE id = ?", Integer.class, session.id()))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM tutor_workflow_run_projection WHERE session_id = ?",
                Integer.class,
                session.id()))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM tutor_workflow_agent_state WHERE session_id = ?",
                Integer.class,
                session.id()))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM tutor_workflow_action_event WHERE session_id = ?",
                Integer.class,
                session.id()))
        .isZero();
  }

  @Test
  void submissionFeedbackPersistsStateAndEventInOneTransaction() {
    repository.persistSubmissionFeedback(
        session.id(),
        new TutorWorkflowRepository.SubmissionFeedbackProjection(
            4,
            "AC_REVIEW",
            "AC",
            "{\"student_state\":{\"attempt_count\":2},\"episode_plan\":null,\"chat_history\":[]}",
            "{}",
            "{\"verdict\":\"AC\",\"accepted\":true}"));

    assertThat(
            jdbc.queryForObject(
                "SELECT phase FROM tutor_workflow_session WHERE id = ?",
                String.class,
                session.id()))
        .isEqualTo("AC_REVIEW");
    assertThat(
            jdbc.queryForObject(
                "SELECT state_json->'student_state'->>'attempt_count' FROM"
                    + " tutor_workflow_agent_state WHERE session_id = ?",
                String.class,
                session.id()))
        .isEqualTo("2");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM tutor_workflow_action_event WHERE session_id = ?"
                    + " AND event_type = 'SUBMISSION_OBSERVED' AND action_key = 'AC'",
                Integer.class,
                session.id()))
        .isEqualTo(1);
  }

  @Test
  void activeTransferLookupUsesTheOwningWorkflowSessionAndLatestCard() {
    TutorWorkflowSessionDto otherSession =
        repository.insertSession(userId, session.problemId() + 1, "wf-" + userId + "-477-cafe");
    jdbc.update(
        "UPDATE tutor_workflow_session SET phase = 'TRANSFER' WHERE id IN (?, ?)",
        session.id(),
        otherSession.id());
    jdbc.update(
        """
        INSERT INTO tutor_workflow_card (session_id, seq, event, card_key, payload)
        VALUES (?, 4, 'TRANSFER', 'transfer_problem', '{"problem_id": 500}'::jsonb),
               (?, 5, 'TRANSFER', 'transfer_problem', '{"problem_id": 501}'::jsonb),
               (?, 5, 'TRANSFER', 'transfer_problem', '{"problem_id": 501}'::jsonb)
        """,
        session.id(),
        session.id(),
        otherSession.id());

    assertThat(repository.findActiveTransferSession(userId, session.sessionKey(), 500L)).isEmpty();
    assertThat(repository.findActiveTransferSession(userId, session.sessionKey(), 501L))
        .contains(
            new TutorWorkflowSessionDto(
                session.id(), session.sessionKey(), session.problemId(), "TRANSFER", 0, "ACTIVE"));
    assertThat(repository.findActiveTransferSession(userId, otherSession.sessionKey(), 501L))
        .contains(
            new TutorWorkflowSessionDto(
                otherSession.id(),
                otherSession.sessionKey(),
                otherSession.problemId(),
                "TRANSFER",
                0,
                "ACTIVE"));
    assertThat(repository.findActiveTransferSession(userId + 1, session.sessionKey(), 501L))
        .isEmpty();
  }

  @Test
  void invalidSubmissionEventRollsBackStateWrite() {
    assertThatThrownBy(
            () ->
                repository.persistSubmissionFeedback(
                    session.id(),
                    new TutorWorkflowRepository.SubmissionFeedbackProjection(
                        4,
                        "ERROR_FEEDBACK",
                        "WA",
                        "{\"student_state\":{\"attempt_count\":1}}",
                        "{}",
                        "not-json")))
        .isInstanceOf(RuntimeException.class);

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM tutor_workflow_agent_state WHERE session_id = ?",
                Integer.class,
                session.id()))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM tutor_workflow_action_event WHERE session_id = ?",
                Integer.class,
                session.id()))
        .isZero();
  }

  private TutorWorkflowRepository.RunProjection projection(
      UUID runId, String phase, int seq, String event, String cardKey, String cardPayload) {
    return new TutorWorkflowRepository.RunProjection(
        runId,
        phase,
        seq,
        event,
        "{\"student_state\":{},\"episode_plan\":null,\"chat_history\":[]}",
        "{}",
        cardKey,
        cardPayload,
        "{\"event\":\"" + event + "\",\"run_id\":\"" + runId + "\"}",
        "{\"session_id\":\"wf-test\",\"event\":\""
            + event
            + "\","
            + "\"phase\":\""
            + phase
            + "\",\"card_key\":"
            + (cardKey == null ? "null" : "\"" + cardKey + "\"")
            + ",\"card\":"
            + (cardPayload == null ? "null" : cardPayload)
            + ",\"available_actions\":[],\"seq\":"
            + seq
            + "}");
  }
}
