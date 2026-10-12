package com.pythontopia.tutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.tutor.dto.TutorWorkflowSessionDto;
import java.util.UUID;
import org.flywaydb.core.Flyway;
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
              DockerImageName.parse("pgvector/pgvector:pg16")
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("pythontopia")
          .withUsername("pytopia")
          .withPassword("pytopia");

  private static JdbcTemplate jdbc;
  private static TutorWorkflowRepository repository;
  private Long userId;
  private TutorWorkflowSessionDto session;

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
    jdbc = new JdbcTemplate(dataSource);

    var transactionManager = new DataSourceTransactionManager(dataSource);
    var proxyFactory = new ProxyFactory(new TutorWorkflowRepository(jdbc, new ObjectMapper()));
    proxyFactory.addAdvice(
        new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
    repository = (TutorWorkflowRepository) proxyFactory.getProxy();
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
  void runProjectionCommitsOnceForRunId() {
    UUID runId = UUID.randomUUID();
    TutorWorkflowRepository.RunProjection projection =
        projection(runId, "CODING", 2, "PLAN_START", "plan_card", "{\"content\":\"started\"}");

    assertThat(repository.persistRunProjection(session.id(), projection)).isTrue();
    assertThat(repository.persistRunProjection(session.id(), projection)).isFalse();

    assertThat(jdbc.queryForObject(
            "SELECT phase FROM tutor_workflow_session WHERE id = ?", String.class, session.id()))
        .isEqualTo("CODING");
    assertThat(jdbc.queryForObject(
            "SELECT seq FROM tutor_workflow_session WHERE id = ?", Integer.class, session.id()))
        .isEqualTo(2);
    assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM tutor_workflow_run_projection WHERE session_id = ?",
            Integer.class,
            session.id()))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM tutor_workflow_card WHERE session_id = ?",
            Integer.class,
            session.id()))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM tutor_workflow_action_event WHERE session_id = ?",
            Integer.class,
            session.id()))
        .isEqualTo(1);
  }

  @Test
  void failedCardInsertRollsBackRunMarkerProgressStateAndEvent() {
    UUID runId = UUID.randomUUID();
    TutorWorkflowRepository.RunProjection projection =
        projection(runId, "CODING", 3, "PLAN_RESPONSE", "ai_reply", "not-json");

    assertThatThrownBy(() -> repository.persistRunProjection(session.id(), projection))
        .isInstanceOf(RuntimeException.class);

    assertThat(jdbc.queryForObject(
            "SELECT phase FROM tutor_workflow_session WHERE id = ?", String.class, session.id()))
        .isEqualTo("READING");
    assertThat(jdbc.queryForObject(
            "SELECT seq FROM tutor_workflow_session WHERE id = ?", Integer.class, session.id()))
        .isZero();
    assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM tutor_workflow_run_projection WHERE session_id = ?",
            Integer.class,
            session.id()))
        .isZero();
    assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM tutor_workflow_agent_state WHERE session_id = ?",
            Integer.class,
            session.id()))
        .isZero();
    assertThat(jdbc.queryForObject(
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
            "AC",
            "{\"student_state\":{\"attempt_count\":2},\"episode_plan\":null,\"chat_history\":[]}",
            "{}",
            "{\"verdict\":\"AC\",\"accepted\":true}"));

    assertThat(jdbc.queryForObject(
            "SELECT state_json->'student_state'->>'attempt_count' FROM tutor_workflow_agent_state"
                + " WHERE session_id = ?",
            String.class,
            session.id()))
        .isEqualTo("2");
    assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM tutor_workflow_action_event WHERE session_id = ?"
                + " AND event_type = 'SUBMISSION_OBSERVED' AND action_key = 'AC'",
            Integer.class,
            session.id()))
        .isEqualTo(1);
  }

  @Test
  void invalidSubmissionEventRollsBackStateWrite() {
    assertThatThrownBy(
            () ->
                repository.persistSubmissionFeedback(
                    session.id(),
                    new TutorWorkflowRepository.SubmissionFeedbackProjection(
                        4,
                        "WA",
                        "{\"student_state\":{\"attempt_count\":1}}",
                        "{}",
                        "not-json")))
        .isInstanceOf(RuntimeException.class);

    assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM tutor_workflow_agent_state WHERE session_id = ?",
            Integer.class,
            session.id()))
        .isZero();
    assertThat(jdbc.queryForObject(
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
        "{\"event\":\"" + event + "\",\"run_id\":\"" + runId + "\"}");
  }
}
