package com.pythontopia.auth.repository;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.dao.DuplicateKeyException;
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

  private static void insertUser(JdbcTemplate jdbc, String username, String email) {
    jdbc.update(
        "INSERT INTO users (username, display_name, password_hash, email, role)"
            + " VALUES (?, ?, 'encoded', ?, 'STUDENT')",
        username,
        username,
        email);
  }
}
