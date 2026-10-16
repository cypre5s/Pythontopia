package com.pythontopia.learner.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.web.StudentWriteThrottle;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
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

@Testcontainers
class LearningEventBatchAtomicityPostgresTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("pythontopia")
          .withUsername("pytopia")
          .withPassword("pytopia");

  private static JdbcTemplate jdbc;
  private static DataSourceTransactionManager transactions;

  @BeforeAll
  static void setUpDatabase() {
    DriverManagerDataSource source = new DriverManagerDataSource();
    source.setDriverClassName("org.postgresql.Driver");
    source.setUrl(POSTGRES.getJdbcUrl());
    source.setUsername(POSTGRES.getUsername());
    source.setPassword(POSTGRES.getPassword());
    jdbc = new JdbcTemplate(source);
    transactions = new DataSourceTransactionManager(source);
    jdbc.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, username TEXT NOT NULL)");
    jdbc.execute(
        "CREATE TABLE learning_event_log (id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL "
            + "REFERENCES users(id), event_type VARCHAR(64) NOT NULL, payload JSONB NOT NULL, "
            + "source VARCHAR(32) NOT NULL, importance INTEGER NOT NULL, created_at TIMESTAMPTZ "
            + "NOT NULL DEFAULT now())");
    jdbc.execute(
        "CREATE FUNCTION reject_selected_learning_event() RETURNS trigger LANGUAGE plpgsql "
            + "AS $$ BEGIN IF NEW.payload->>'forceFailure' = 'true' THEN "
            + "RAISE EXCEPTION 'injected learning event failure'; END IF; RETURN NEW; END $$");
    jdbc.execute(
        "CREATE TRIGGER reject_selected_learning_event BEFORE INSERT ON learning_event_log "
            + "FOR EACH ROW EXECUTE FUNCTION reject_selected_learning_event()");
    jdbc.update("INSERT INTO users (id, username) VALUES (7, 'student')");
  }

  @Test
  void databaseFailureRollsBackEveryEventInBatch() {
    LearningEventController controller =
        new LearningEventController(
            new LearningEventLogRepository(jdbc, new ObjectMapper()),
            new ClientLearningEventGuard(new ObjectMapper(), new StudentWriteThrottle()));
    ProxyFactory proxyFactory = new ProxyFactory(controller);
    proxyFactory.setProxyTargetClass(true);
    proxyFactory.addAdvice(
        new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
    LearningEventController transactionalController =
        (LearningEventController) proxyFactory.getProxy();
    List<LearningEventLogDto> batch =
        List.of(
            new LearningEventLogDto("answer_feedback", Map.of("useful", true)),
            new LearningEventLogDto("quiz_finished", Map.of("forceFailure", true)));

    assertThatThrownBy(
            () ->
                transactionalController.logBatch(
                    new CurrentUserPrincipal(7L, "student", Role.STUDENT), batch))
        .isInstanceOf(RuntimeException.class);

    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM learning_event_log", Integer.class))
        .isZero();
  }
}
