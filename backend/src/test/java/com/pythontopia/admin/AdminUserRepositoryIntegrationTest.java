package com.pythontopia.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.pythontopia.auth.security.Role;
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
class AdminUserRepositoryIntegrationTest {

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
    DriverManagerDataSource source = new DriverManagerDataSource();
    source.setDriverClassName("org.postgresql.Driver");
    source.setUrl(POSTGRES.getJdbcUrl());
    source.setUsername(POSTGRES.getUsername());
    source.setPassword(POSTGRES.getPassword());
    Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
    jdbc = new JdbcTemplate(source);
  }

  @Test
  void roleFilterAppliesToPostgresContentAndCountQueries() {
    String suffix = Long.toString(System.nanoTime());
    insertUser("admin-filter-student-" + suffix, "STUDENT");
    String teacherUsername = "admin-filter-teacher-" + suffix;
    insertUser(teacherUsername, "TEACHER");
    insertUser("admin-filter-admin-" + suffix, "ADMIN");

    AdminUserPageDto result = new AdminUserRepository(jdbc).page(0, 10, null, null, Role.TEACHER);

    assertThat(result.totalElements()).isEqualTo(1);
    assertThat(result.content())
        .singleElement()
        .satisfies(user -> {
          assertThat(user.username()).isEqualTo(teacherUsername);
          assertThat(user.role()).isEqualTo(Role.TEACHER);
        });
  }

  @Test
  void studentDetailQueriesUseExistingProfileActivityJudgeAndClassTables() {
    long studentId = insertTestUser("admin-detail-student-" + System.nanoTime(), "STUDENT");
    jdbc.update(
        "INSERT INTO learning_event_log (user_id, event_type, payload, source)"
            + " VALUES (?, 'submission_passed', '{}'::jsonb, 'backend')",
        studentId);

    AdminStudentDetailRepository repository = new AdminStudentDetailRepository(jdbc);

    assertThat(repository.findStudent(studentId)).get().satisfies(account -> {
      assertThat(account.id()).isEqualTo(studentId);
      assertThat(account.role()).isEqualTo(Role.STUDENT);
    });
    assertThat(repository.findMastery(studentId)).isEmpty();
    assertThat(repository.findActivity(studentId))
        .singleElement()
        .satisfies(activity -> assertThat(activity.eventType()).isEqualTo("submission_passed"));
    assertThat(repository.findSubmissions(studentId)).isEmpty();
    assertThat(repository.findClassrooms(studentId)).isEmpty();
  }

  @Test
  void courseResourceCatalogQueriesExistingSeedTables() {
    AdminCourseResourceRepository repository = new AdminCourseResourceRepository(jdbc);
    AdminCourseResourcePageDto result = repository.page(0, 50, null, AdminCourseResourceType.ALL);

    assertThat(result.page()).isZero();
    assertThat(result.size()).isEqualTo(50);
    assertThat(result.totalElements()).isEqualTo(164);
    assertThat(result.totalPages()).isEqualTo(4);
    assertThat(result.content()).hasSize(50);
    assertThat(repository.page(0, 50, null, AdminCourseResourceType.DOCUMENT).totalElements())
        .isEqualTo(7);
  }

  @Test
  void usageHistoryKeepsUnknownTokenCountsUnknown() {
    long userId = insertTestUser("admin-usage-student-" + System.nanoTime(), "STUDENT");
    jdbc.update(
        "INSERT INTO loop_run (user_id, session_id, input_tokens, output_tokens, trace_id) VALUES (?, 'admin-test', 18, 7, 'admin-test-trace')",
        userId);
    jdbc.update(
        "INSERT INTO resource_run (user_id, agent_name, scenario, status)"
            + " VALUES (?, 'ResourceDesignerAgent', 'WORKSHOP', 'SUCCESS')",
        userId);

    AdminUsageDto result = new AdminUsageRepository(jdbc).list(365, "admin-usage");

    assertThat(result.totalEvents()).isEqualTo(2);
    assertThat(result.inputTokens()).isEqualTo(18);
    assertThat(result.outputTokens()).isEqualTo(7);
    assertThat(result.unknownTokenEvents()).isEqualTo(1);
    assertThat(result.content()).extracting(AdminUsageDto.Event::inputTokens)
        .contains(null, 18);
  }

  @Test
  void aiProviderConfigStoresOnlyCiphertextAndOneActiveRow() {
    long adminId = insertTestUser("admin-config-admin-" + System.nanoTime(), "ADMIN");
    AdminAiConfigRepository repository = new AdminAiConfigRepository(jdbc);
    String ciphertext = "nonce-and-authenticated-ciphertext";

    repository.save("deepseek-v4-pro", ciphertext, adminId);
    repository.save("deepseek-flash", ciphertext, adminId);

    assertThat(repository.find())
        .get()
        .satisfies(config -> {
          assertThat(config.model()).isEqualTo("deepseek-flash");
          assertThat(config.encryptedApiKey()).isEqualTo(ciphertext);
        });
    assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_provider_config", Integer.class))
        .isEqualTo(1);
  }

  private void insertUser(String username, String role) {
    jdbc.update(
        "INSERT INTO users (username, display_name, password_hash, role) VALUES (?, ?, 'hash', ?)",
        username,
        username,
        role);
  }

  private long insertTestUser(String username, String role) {
    return jdbc.queryForObject(
        "INSERT INTO users (username, display_name, password_hash, role)"
            + " VALUES (?, ?, 'hash', ?) RETURNING id",
        Long.class,
        username,
        username,
        role);
  }
}
