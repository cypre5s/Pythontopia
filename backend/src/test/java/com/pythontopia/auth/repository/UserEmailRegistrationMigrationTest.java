package com.pythontopia.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pythontopia.auth.dto.LoginRequest;
import com.pythontopia.auth.repository.UserRepository;
import com.pythontopia.auth.security.JwtService;
import com.pythontopia.auth.security.TeacherInviteProperties;
import com.pythontopia.auth.service.AuthService;
import com.pythontopia.common.error.BusinessException;
import javax.sql.DataSource;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.mockito.Mockito;
import org.springframework.aop.framework.ProxyFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class UserEmailRegistrationMigrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:pg16")
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("pythontopia")
          .withUsername("pytopia")
          .withPassword("pytopia");

  @Test
  void optionalEmailMayBeSharedWhileUsernameRemainsUnique() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.postgresql.Driver");
    dataSource.setUrl(POSTGRES.getJdbcUrl());
    dataSource.setUsername(POSTGRES.getUsername());
    dataSource.setPassword(POSTGRES.getPassword());
    Flyway.configure().dataSource((DataSource) dataSource).locations("classpath:db/migration").load().migrate();
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);

    insertUser(jdbc, "student-one", "shared@example.edu");
    assertThatCode(() -> insertUser(jdbc, "student-two", "shared@example.edu"))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> insertUser(jdbc, "student-one", "other@example.edu"))
        .isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  void accountLoginFailureCooldownEscalatesAndResets() {
    DataSource dataSource = migrateAndConnect();
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    String username = "login-test-" + UUID.randomUUID();
    insertUser(jdbc, username, null);
    Long userId =
        jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
    UserRepository repository = new UserRepository(jdbc);

    for (int attempt = 1; attempt < 5; attempt++) {
      repository.recordFailedLogin(userId);
      assertThat(repository.isLoginThrottled(userId)).isFalse();
    }
    repository.recordFailedLogin(userId);
    assertThat(repository.isLoginThrottled(userId)).isTrue();
    assertThat(loginFailureCount(jdbc, userId)).isEqualTo(5);
    assertThat(cooldownSeconds(jdbc, userId)).isEqualTo(1);

    jdbc.update(
        "UPDATE users SET login_blocked_until = now() - interval '1 second' WHERE id = ?", userId);
    repository.recordFailedLogin(userId);
    assertThat(cooldownSeconds(jdbc, userId)).isEqualTo(2);
    for (int attempt = 7; attempt <= 10; attempt++) {
      jdbc.update(
          "UPDATE users SET login_blocked_until = now() - interval '1 second' WHERE id = ?",
          userId);
      repository.recordFailedLogin(userId);
    }
    assertThat(cooldownSeconds(jdbc, userId)).isEqualTo(8);
    for (int attempt = 0; attempt < 20; attempt++) {
      jdbc.update(
          "UPDATE users SET login_blocked_until = now() - interval '1 second' WHERE id = ?",
          userId);
      repository.recordFailedLogin(userId);
    }
    assertThat(loginFailureCount(jdbc, userId)).isEqualTo(7);

    jdbc.update(
        "UPDATE users SET login_failure_window_started_at = now() - interval '16 minutes'"
            + " WHERE id = ?",
        userId);
    repository.recordFailedLogin(userId);
    assertThat(loginFailureCount(jdbc, userId)).isEqualTo(1);
    assertThat(repository.isLoginThrottled(userId)).isFalse();
    repository.clearLoginFailures(userId);
    assertThat(repository.isLoginThrottled(userId)).isFalse();
    assertThat(loginFailureCount(jdbc, userId)).isZero();
  }

  @Test
  void concurrentIncorrectLoginsCannotBypassAccountCooldown() throws Exception {
    DataSource dataSource = migrateAndConnect();
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    String username = "parallel-login-" + UUID.randomUUID();
    insertUser(jdbc, username, null);
    Long userId = jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
    AtomicInteger storedPasswordChecks = new AtomicInteger();
    PasswordEncoder encoder =
        new PasswordEncoder() {
          @Override
          public String encode(CharSequence rawPassword) {
            return rawPassword.toString();
          }

          @Override
          public boolean matches(CharSequence rawPassword, String encodedPassword) {
            if ("encoded".equals(encodedPassword)) {
              storedPasswordChecks.incrementAndGet();
              try {
                Thread.sleep(300);
              } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
              }
            }
            return rawPassword.toString().equals(encodedPassword);
          }
        };
    AuthService target =
        new AuthService(
            new UserRepository(jdbc),
            encoder,
            Mockito.mock(JwtService.class),
            new TeacherInviteProperties("test-invite-value-0123456789"));
    ProxyFactory proxyFactory = new ProxyFactory(target);
    proxyFactory.setProxyTargetClass(true);
    proxyFactory.addAdvice(
        new TransactionInterceptor(
            new DataSourceTransactionManager(dataSource), new AnnotationTransactionAttributeSource()));
    AuthService service = (AuthService) proxyFactory.getProxy();

    int concurrentRequests = 20;
    CountDownLatch ready = new CountDownLatch(concurrentRequests);
    CountDownLatch start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(concurrentRequests);
    List<java.util.concurrent.Future<Boolean>> responses = new ArrayList<>();
    try {
      for (int attempt = 0; attempt < concurrentRequests; attempt++) {
        responses.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  start.await();
                  try {
                    service.login(new LoginRequest(username, "wrong-password"));
                    return false;
                  } catch (BusinessException exception) {
                    return "BAD_CREDENTIALS".equals(exception.code());
                  }
                }));
      }
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      for (java.util.concurrent.Future<Boolean> response : responses) {
        assertThat(response.get(15, TimeUnit.SECONDS)).isTrue();
      }
    } finally {
      executor.shutdownNow();
    }

    UserRepository repository = new UserRepository(jdbc);
    assertThat(storedPasswordChecks).hasValue(5);
    assertThat(loginFailureCount(jdbc, userId)).isEqualTo(5);
    assertThat(
            jdbc.queryForObject(
                "SELECT login_blocked_until IS NOT NULL FROM users WHERE id = ?", Boolean.class, userId))
        .isTrue();
  }

  private static DataSource migrateAndConnect() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.postgresql.Driver");
    dataSource.setUrl(POSTGRES.getJdbcUrl());
    dataSource.setUsername(POSTGRES.getUsername());
    dataSource.setPassword(POSTGRES.getPassword());
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    return dataSource;
  }

  private static int loginFailureCount(JdbcTemplate jdbc, Long userId) {
    return jdbc.queryForObject(
        "SELECT login_failed_count FROM users WHERE id = ?", Integer.class, userId);
  }

  private static long cooldownSeconds(JdbcTemplate jdbc, Long userId) {
    OffsetDateTime blockedUntil = jdbc.queryForObject(
        "SELECT login_blocked_until FROM users WHERE id = ?", OffsetDateTime.class, userId);
    OffsetDateTime updatedAt = jdbc.queryForObject(
        "SELECT updated_at FROM users WHERE id = ?", OffsetDateTime.class, userId);
    return Duration.between(updatedAt, blockedUntil).getSeconds();
  }

  private static void insertUser(JdbcTemplate jdbc, String username, String email) {
    jdbc.update(
        "INSERT INTO users (username, display_name, password_hash, email, role)"
            + " VALUES (?, ?, 'encoded', ?, 'STUDENT')",
        username,
        username,
        email);
  }
}
