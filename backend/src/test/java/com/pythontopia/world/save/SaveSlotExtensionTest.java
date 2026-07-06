package com.pythontopia.world.save;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.PythontopiaApplication;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
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
 * Integration test for V114 migration: ensures save_slot exposes the four W3 extension columns
 * (scene_name / unlocked_buildings / accumulated_play_seconds / inventory_items) and the
 * repository round-trips them correctly.
 */
@Testcontainers
@SpringBootTest(classes = PythontopiaApplication.class)
class SaveSlotExtensionTest {

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

  @Autowired SaveSlotRepository repository;
  @Autowired SaveSlotService service;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired ObjectMapper objectMapper;

  private Long userId;

  @BeforeEach
  void seedUser() {
    jdbcTemplate.update("DELETE FROM save_slot WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'save-ext-%')");
    jdbcTemplate.update("DELETE FROM users WHERE username LIKE 'save-ext-%'");
    String username = "save-ext-" + System.nanoTime();
    jdbcTemplate.update(
        """
        INSERT INTO users (username, display_name, password_hash, role)
        VALUES (?, 'SaveExt User', 'hash-not-used', 'STUDENT')
        """,
        username);
    userId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM users WHERE username = ?", Long.class, username);
  }

  @Test
  void v114AddsAllFourExtensionColumnsWithExpectedDefaults() {
    Map<String, Object> columns =
        jdbcTemplate
            .queryForList(
                """
                SELECT column_name, data_type, column_default, is_nullable
                FROM information_schema.columns
                WHERE table_name = 'save_slot'
                  AND column_name IN ('scene_name','unlocked_buildings',
                                      'accumulated_play_seconds','inventory_items')
                """)
            .stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    row -> (String) row.get("column_name"), row -> row));

    assertThat(columns).containsKeys(
        "scene_name", "unlocked_buildings", "accumulated_play_seconds", "inventory_items");

    @SuppressWarnings("unchecked")
    Map<String, Object> sceneColumn = (Map<String, Object>) columns.get("scene_name");
    assertThat(sceneColumn).containsEntry("is_nullable", "NO");
    assertThat((String) sceneColumn.get("column_default")).contains("WorldScene");

    @SuppressWarnings("unchecked")
    Map<String, Object> secondsColumn = (Map<String, Object>) columns.get("accumulated_play_seconds");
    assertThat(secondsColumn).containsEntry("is_nullable", "NO");
    assertThat((String) secondsColumn.get("column_default")).startsWith("0");

    @SuppressWarnings("unchecked")
    Map<String, Object> inventoryColumn = (Map<String, Object>) columns.get("inventory_items");
    assertThat((String) inventoryColumn.get("data_type")).isEqualTo("jsonb");
  }

  @Test
  void createSlotReturnsExtensionDefaultsFromMigration() {
    SaveSlot slot = repository.createSlot(userId, 0, "Default save");

    assertThat(slot.sceneName()).isEqualTo("WorldScene");
    assertThat(slot.unlockedBuildings()).isNotNull().isEmpty();
    assertThat(slot.accumulatedPlaySeconds()).isZero();
    assertThat(slot.inventoryItems()).isNotNull();
    assertThat(slot.inventoryItems().isArray()).isTrue();
    assertThat(slot.inventoryItems().size()).isZero();
  }

  @Test
  void updateExtensionRoundTripsAllFourColumns() throws Exception {
    repository.createSlot(userId, 1, "Patched save");

    SaveSlot updated =
        repository.updateExtension(
            userId,
            1,
            "MineSouthScene",
            List.of("library", "mine_entry", "shop_main"),
            7325L,
            objectMapper.writeValueAsString(
                List.of(
                    Map.of("itemId", "seed_print", "qty", 5, "slot", 0),
                    Map.of("itemId", "pickaxe", "qty", 1, "slot", 1))));

    assertThat(updated.sceneName()).isEqualTo("MineSouthScene");
    assertThat(updated.unlockedBuildings())
        .containsExactly("library", "mine_entry", "shop_main");
    assertThat(updated.accumulatedPlaySeconds()).isEqualTo(7325L);
    assertThat(updated.inventoryItems().isArray()).isTrue();
    assertThat(updated.inventoryItems().size()).isEqualTo(2);
    assertThat(updated.inventoryItems().get(0).path("itemId").asText()).isEqualTo("seed_print");
    assertThat(updated.inventoryItems().get(0).path("qty").asInt()).isEqualTo(5);
    assertThat(updated.inventoryItems().get(1).path("itemId").asText()).isEqualTo("pickaxe");
  }

  @Test
  void serviceUpdateExtensionAcceptsListPayloadAndPartialNulls() {
    repository.createSlot(userId, 2, "Service patch save");

    var first =
        service.updateExtension(
            userId,
            2,
            "PortWestScene",
            List.of("port_dock"),
            120L,
            List.of(Map.of("itemId", "fish_lure", "qty", 3, "slot", 0)));

    assertThat(first.sceneName()).isEqualTo("PortWestScene");
    assertThat(first.unlockedBuildings()).containsExactly("port_dock");
    assertThat(first.accumulatedPlaySeconds()).isEqualTo(120L);
    assertThat(first.inventoryItems()).isNotNull();
    assertThat(first.inventoryItems().size()).isEqualTo(1);

    var second = service.updateExtension(userId, 2, null, null, 999L, null);

    assertThat(second.sceneName()).isEqualTo("PortWestScene");
    assertThat(second.unlockedBuildings()).containsExactly("port_dock");
    assertThat(second.accumulatedPlaySeconds()).isEqualTo(999L);
    assertThat(second.inventoryItems().size()).isEqualTo(1);
    assertThat(second.inventoryItems().get(0).path("itemId").asText()).isEqualTo("fish_lure");
  }
}
