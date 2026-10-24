package com.pythontopia.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pythontopia.PythontopiaApplication;
import com.pythontopia.auth.repository.UserRepository;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 初始管理员自举：全新库必须能自动拿到一个 ADMIN，且重复启动/重复调用绝不重置既有口令。
 *
 * <p>用例按顺序执行并共享同一个测试库：先验证启动期自举，再验证弱口令跳过，最后把管理员降级成
 * TEACHER 来验证「用户名已存在时提升而不是新建」。
 */
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(
    classes = PythontopiaApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BootstrapAdminInitializerIntegrationTest {

  private static final String BOOTSTRAP_USERNAME = "bootstrap-admin";
  private static final String BOOTSTRAP_PASSWORD = "bootstrap-admin-password-1";

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:pg16")
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("pythontopia")
          .withUsername("pytopia")
          .withPassword("pytopia");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    registry.add("pythontopia.security.jwt.secret", () -> "bootstrap-admin-test-secret-long-enough");
    registry.add("pythontopia.security.teacherInviteCode", () -> "bootstrap-admin-test-invite");
    registry.add("pythontopia.services.graphUrl", () -> "http://graph.test");
    registry.add("pythontopia.judge.python", () -> "python3");
    registry.add("pythontopia.auth.registration-enabled", () -> "false");
    registry.add("pythontopia.auth.bootstrap-admin.username", () -> BOOTSTRAP_USERNAME);
    registry.add("pythontopia.auth.bootstrap-admin.password", () -> BOOTSTRAP_PASSWORD);
  }

  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired UserRepository userRepository;
  @Autowired BootstrapAdminInitializer initializer;

  @Test
  @Order(1)
  void createsAdminOnStartupAndNeverResetsPassword() {
    // 应用就绪事件已触发自举：空库必须已经有可用管理员
    assertThat(roleOf(BOOTSTRAP_USERNAME)).isEqualTo("ADMIN");
    String initialHash = passwordHash(BOOTSTRAP_USERNAME);
    assertThat(passwordEncoder.matches(BOOTSTRAP_PASSWORD, initialHash)).isTrue();
    int tokenVersionBefore = tokenVersion(BOOTSTRAP_USERNAME);

    // 重复触发（模拟重启与滚动发布）不得新建、不得改口令、不得踢掉现有会话
    initializer.ensureAdminAccount();
    initializer.ensureAdminAccount();

    assertThat(adminCount()).isEqualTo(1);
    assertThat(passwordHash(BOOTSTRAP_USERNAME)).isEqualTo(initialHash);
    assertThat(tokenVersion(BOOTSTRAP_USERNAME)).isEqualTo(tokenVersionBefore);
  }

  @Test
  @Order(2)
  void skipsBootstrapWhenPasswordIsTooShort() {
    new BootstrapAdminInitializer(
            userRepository, passwordEncoder, "bootstrap-admin-weak", "short", "管理员")
        .ensureAdminAccount();

    assertThat(userExists("bootstrap-admin-weak")).isFalse();
  }

  @Test
  @Order(3)
  void promotesExistingAccountInsteadOfCreatingDuplicate() {
    // 模拟「库里没有 ADMIN，但目标用户名已被一个 TEACHER 占用」
    jdbcTemplate.update("DELETE FROM users WHERE username = ?", BOOTSTRAP_USERNAME);
    jdbcTemplate.update(
        "INSERT INTO users (username, display_name, password_hash, email, role)"
            + " VALUES (?, ?, ?, NULL, 'TEACHER')",
        BOOTSTRAP_USERNAME,
        "旧教师",
        passwordEncoder.encode("original-password-1"));
    int tokenVersionBefore = tokenVersion(BOOTSTRAP_USERNAME);

    initializer.ensureAdminAccount();

    assertThat(roleOf(BOOTSTRAP_USERNAME)).isEqualTo("ADMIN");
    // 提权必须让已签发的 JWT 立即失效
    assertThat(tokenVersion(BOOTSTRAP_USERNAME)).isEqualTo(tokenVersionBefore + 1);
    // 提权不得覆盖原口令
    assertThat(
            passwordEncoder.matches(
                "original-password-1", passwordHash(BOOTSTRAP_USERNAME)))
        .isTrue();
  }

  private String roleOf(String username) {
    return jdbcTemplate.queryForObject(
        "SELECT role FROM users WHERE username = ?", String.class, username);
  }

  private String passwordHash(String username) {
    return jdbcTemplate.queryForObject(
        "SELECT password_hash FROM users WHERE username = ?", String.class, username);
  }

  private int tokenVersion(String username) {
    return jdbcTemplate.queryForObject(
        "SELECT token_version FROM users WHERE username = ?", Integer.class, username);
  }

  private boolean userExists(String username) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM users WHERE username = ?)", Boolean.class, username));
  }

  private int adminCount() {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM users WHERE role = 'ADMIN'", Integer.class);
  }
}
