package com.pythontopia.world.energy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pythontopia.PythontopiaApplication;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * R-12 W5 V115 migration 集成测试：
 * 校验 {@code world_session} 表新增的 {@code energy / energy_cap / overwork_minutes} 三列
 * 默认值、is_nullable、check 约束（0-100 / 60-110 / non-negative）以及插入行为。
 *
 * <p>本测试只校验 schema 一面真相，业务层 {@code EnergyService.applyDelta} 由后续单测覆盖，
 * 不在本骨架 PR 中实现。
 */
@Testcontainers
@SpringBootTest(classes = PythontopiaApplication.class)
class EnergySchemaTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:pg16")
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("pythontopia")
          .withUsername("pytopia")
          .withPassword("pytopia");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    registry.add(
        "pythontopia.security.jwt.secret",
        () -> "test-secret-must-be-long-enough-for-hs256");
    registry.add("pythontopia.security.teacherInviteCode", () -> "test-teacher-code");
  }

  @Autowired JdbcTemplate jdbcTemplate;

  private Long userId;

  @BeforeEach
  void seedUser() {
    jdbcTemplate.update(
        "DELETE FROM world_session WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'energy-schema-%')");
    jdbcTemplate.update("DELETE FROM users WHERE username LIKE 'energy-schema-%'");
    String username = "energy-schema-" + System.nanoTime();
    jdbcTemplate.update(
        """
        INSERT INTO users (username, display_name, password_hash, role)
        VALUES (?, 'Energy Schema User', 'hash-not-used', 'STUDENT')
        """,
        username);
    userId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM users WHERE username = ?", Long.class, username);
  }

  @Test
  void v115AddsAllThreeEnergyColumnsWithExpectedDefaultsAndNullability() {
    Map<String, Map<String, Object>> columns =
        jdbcTemplate
            .queryForList(
                """
                SELECT column_name, data_type, column_default, is_nullable
                FROM information_schema.columns
                WHERE table_name = 'world_session'
                  AND column_name IN ('energy', 'energy_cap', 'overwork_minutes')
                """)
            .stream()
            .collect(Collectors.toMap(row -> (String) row.get("column_name"), row -> row));

    assertThat(columns).containsKeys("energy", "energy_cap", "overwork_minutes");

    Map<String, Object> energy = columns.get("energy");
    assertThat(energy).containsEntry("is_nullable", "NO");
    assertThat((String) energy.get("data_type")).isEqualTo("integer");
    assertThat((String) energy.get("column_default")).startsWith("100");

    Map<String, Object> cap = columns.get("energy_cap");
    assertThat(cap).containsEntry("is_nullable", "NO");
    assertThat((String) cap.get("data_type")).isEqualTo("integer");
    assertThat((String) cap.get("column_default")).startsWith("100");

    Map<String, Object> overwork = columns.get("overwork_minutes");
    assertThat(overwork).containsEntry("is_nullable", "NO");
    assertThat((String) overwork.get("data_type")).isEqualTo("integer");
    assertThat((String) overwork.get("column_default")).startsWith("0");
  }

  @Test
  void v115RegistersRangeAndNonNegativeCheckConstraints() {
    List<String> constraintNames =
        jdbcTemplate.queryForList(
            """
            SELECT conname
            FROM pg_constraint c
            JOIN pg_class t ON t.oid = c.conrelid
            WHERE t.relname = 'world_session'
              AND c.contype = 'c'
            """,
            String.class);

    assertThat(constraintNames)
        .contains(
            "world_session_energy_range",
            "world_session_energy_cap_range",
            "world_session_overwork_minutes_non_negative");
  }

  @Test
  void newWorldSessionRowGetsDefaultEnergyFullCapAndZeroOverwork() {
    insertWorldSessionForCurrentUser();

    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT energy, energy_cap, overwork_minutes FROM world_session WHERE user_id = ?",
            userId);

    assertThat(((Number) row.get("energy")).intValue()).isEqualTo(100);
    assertThat(((Number) row.get("energy_cap")).intValue()).isEqualTo(100);
    assertThat(((Number) row.get("overwork_minutes")).intValue()).isZero();
  }

  @Test
  void energyOutsideZeroToHundredIsRejectedByCheckConstraint() {
    insertWorldSessionForCurrentUser();

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE world_session SET energy = ? WHERE user_id = ?", -1, userId))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE world_session SET energy = ? WHERE user_id = ?", 101, userId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void energyCapOutsideSixtyToHundredTenIsRejectedByCheckConstraint() {
    insertWorldSessionForCurrentUser();

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE world_session SET energy_cap = ? WHERE user_id = ?", 59, userId))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE world_session SET energy_cap = ? WHERE user_id = ?", 111, userId))
        .isInstanceOf(DataIntegrityViolationException.class);

    int affected =
        jdbcTemplate.update(
            "UPDATE world_session SET energy_cap = ? WHERE user_id = ?", 80, userId);
    assertThat(affected).isOne();
  }

  @Test
  void overworkMinutesMustBeNonNegative() {
    insertWorldSessionForCurrentUser();

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE world_session SET overwork_minutes = ? WHERE user_id = ?", -1, userId))
        .isInstanceOf(DataIntegrityViolationException.class);

    int affected =
        jdbcTemplate.update(
            "UPDATE world_session SET overwork_minutes = ? WHERE user_id = ?", 240, userId);
    assertThat(affected).isOne();
  }

  private void insertWorldSessionForCurrentUser() {
    jdbcTemplate.update(
        """
        INSERT INTO world_session (user_id, day_index, weather, in_game_minutes)
        VALUES (?, 1, 'sunny', 480)
        """,
        userId);
  }
}
