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

  private void insertUser(String username, String role) {
    jdbc.update(
        "INSERT INTO users (username, display_name, password_hash, role) VALUES (?, ?, 'hash', ?)",
        username,
        username,
        role);
  }
}
