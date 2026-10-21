package com.pythontopia.safety.pyodide;

import static org.assertj.core.api.Assertions.assertThat;

import com.pythontopia.PythontopiaApplication;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Schema smoke test for the V116 {@code pyodide_execution} audit table backing the W4 Pyodide
 * budget design (docs/gameplay/r12-deepen/08-pyodide-budget.md §9.1):
 *
 * <ul>
 *   <li>verifies the table is created by Flyway after V116 runs;
 *   <li>verifies the four core audit columns (user_id / started_at / duration_ms / status) exist
 *       with the correct types and NOT NULL constraints.
 * </ul>
 *
 * <p>Implementation note: the task description references {@code @DataJdbcTest}, but this project
 * ships with {@code spring-boot-starter-data-jpa} (not {@code spring-boot-starter-data-jdbc}), so
 * {@code @DataJdbcTest} would fail to bootstrap. Following the existing V114
 * {@code SaveSlotExtensionTest} pattern, this class uses {@code @SpringBootTest} +
 * {@code @Testcontainers} + a raw {@code JdbcTemplate} to introspect {@code information_schema}.
 */
@Testcontainers
@SpringBootTest(classes = PythontopiaApplication.class)
class PyodideSchemaTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("pythontopia")
          .withUsername("pytopia")
          .withPassword("pytopia");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    // The full application context starts services that depend on tables added after V116, so
    // migrate the complete schema before checking the Pyodide audit table.
    registry.add(
        "pythontopia.security.jwt.secret", () -> "test-secret-must-be-long-enough-for-hs256");
    registry.add("pythontopia.security.teacherInviteCode", () -> "test-teacher-code");
  }

  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  void pyodideExecutionTableExistsAfterMigration() {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?",
            Long.class,
            "pyodide_execution");
    assertThat(count).isEqualTo(1L);
  }

  @Test
  void pyodideExecutionExposesFourRequiredColumns() {
    Map<String, Map<String, Object>> columns =
        jdbcTemplate
            .queryForList(
                """
                SELECT column_name, data_type, is_nullable, column_default
                FROM information_schema.columns
                WHERE table_name = 'pyodide_execution'
                  AND column_name IN ('user_id', 'started_at', 'duration_ms', 'status')
                """)
            .stream()
            .collect(Collectors.toMap(row -> (String) row.get("column_name"), row -> row));

    assertThat(columns).containsKeys("user_id", "started_at", "duration_ms", "status");

    Map<String, Object> userId = columns.get("user_id");
    assertThat(userId).containsEntry("data_type", "bigint").containsEntry("is_nullable", "NO");

    Map<String, Object> startedAt = columns.get("started_at");
    assertThat(startedAt)
        .containsEntry("data_type", "timestamp with time zone")
        .containsEntry("is_nullable", "NO");

    Map<String, Object> durationMs = columns.get("duration_ms");
    assertThat(durationMs).containsEntry("data_type", "integer").containsEntry("is_nullable", "NO");

    Map<String, Object> status = columns.get("status");
    assertThat(status)
        .containsEntry("data_type", "character varying")
        .containsEntry("is_nullable", "NO");
  }
}
