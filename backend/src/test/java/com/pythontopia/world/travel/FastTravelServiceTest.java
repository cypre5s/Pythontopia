package com.pythontopia.world.travel;

import static org.assertj.core.api.Assertions.assertThat;

import com.pythontopia.world.travel.FastTravelService.FastTravelResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Isolated JDBC test for R-14 shrunk fast travel: V119 seed + V131 cleanup leaves only
 * {@code town_hub ↔ mine_south}. Mirrors production Flyway chain without booting Spring.
 */
@Testcontainers
class FastTravelServiceTest {

  private static final long TEST_USER_ID = 1L;

  private static final Pattern LINE_COMMENT = Pattern.compile("--[^\\n]*");

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:pg16")
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("pythontopia")
          .withUsername("pytopia")
          .withPassword("pytopia");

  private static JdbcTemplate jdbcTemplate;
  private static FastTravelRepository repository;
  private static FastTravelService service;

  @BeforeAll
  static void bootstrap() throws IOException {
    DataSource dataSource = newDataSource();
    jdbcTemplate = new JdbcTemplate(dataSource);
    runScript(readMigration("V119__fast_travel_route.sql"));
    runScript(readMigration("V131__fast_travel_r14_shrink.sql"));

    repository = new FastTravelRepository(jdbcTemplate);
    service = new FastTravelService(repository);
  }

  private static String readMigration(String fileName) throws IOException {
    return Files.readString(
        Paths.get("src/main/resources/db/migration/" + fileName), StandardCharsets.UTF_8);
  }

  private static DataSource newDataSource() {
    DriverManagerDataSource ds = new DriverManagerDataSource();
    ds.setUrl(POSTGRES.getJdbcUrl());
    ds.setUsername(POSTGRES.getUsername());
    ds.setPassword(POSTGRES.getPassword());
    ds.setDriverClassName("org.postgresql.Driver");
    return ds;
  }

  private static void runScript(String sql) {
    String stripped = LINE_COMMENT.matcher(sql).replaceAll("");
    Arrays.stream(stripped.split(";"))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .forEach(jdbcTemplate::execute);
  }

  @Test
  void r14SeedKeepsOnlyHubMineRoutes() {
    assertThat(repository.findAll()).hasSize(2);
    assertThat(repository.find("town_hub", "mine_south")).isPresent();
    assertThat(repository.find("mine_south", "town_hub")).isPresent();
    assertThat(repository.find("town_hub", "farm_north")).isEmpty();
  }

  @Test
  void findReturnsKnownTownHubToMineSouthRoute() {
    FastTravelRoute route = repository.find("town_hub", "mine_south").orElseThrow();

    assertThat(route.fromScene()).isEqualTo("town_hub");
    assertThat(route.toScene()).isEqualTo("mine_south");
    assertThat(route.unlockPhase()).isEqualTo(1);
    assertThat(route.costPythoco()).isEqualTo(10);
    assertThat(route.costEnergy()).isEqualTo(5);
    assertThat(route.costMinutes()).isEqualTo(60);
  }

  @Test
  void findUnlockedFromTownHubAtPhase1OnlyMineSouth() {
    assertThat(repository.findUnlockedFromScene("town_hub", 0)).isEmpty();
    assertThat(repository.findUnlockedFromScene("town_hub", 1))
        .extracting(FastTravelRoute::toScene)
        .containsExactly("mine_south");
  }

  @Test
  void canTravelAcceptsTownHubToMineSouthAtPhase1() {
    assertThat(service.canTravel(TEST_USER_ID, "town_hub", "mine_south", 1, 100, 50)).isTrue();
  }

  @Test
  void canTravelRejectsTownHubToMineSouthAtPhase0() {
    assertThat(service.canTravel(TEST_USER_ID, "town_hub", "mine_south", 0, 100, 50)).isFalse();
  }

  @Test
  void canTravelRejectsWhenPythocoBelowCost() {
    assertThat(service.canTravel(TEST_USER_ID, "town_hub", "mine_south", 1, 5, 50)).isFalse();
  }

  @Test
  void canTravelRejectsWhenEnergyBelowFloor() {
    assertThat(service.canTravel(TEST_USER_ID, "town_hub", "mine_south", 1, 100, 9)).isFalse();
  }

  @Test
  void travelDeductsCostsOnSuccess() {
    FastTravelResult result =
        service.travel(TEST_USER_ID, "town_hub", "mine_south", 1, 100, 50, 0);

    assertThat(result.success()).isTrue();
    assertThat(result.reason()).isEqualTo("OK");
    assertThat(result.newPythoco()).isEqualTo(90);
    assertThat(result.newEnergy()).isEqualTo(45);
    assertThat(result.minutesPassed()).isEqualTo(60);
  }

  @Test
  void travelRejectsAtDailyCapTen() {
    FastTravelResult result =
        service.travel(TEST_USER_ID, "town_hub", "mine_south", 1, 100, 50, 10);

    assertThat(result.success()).isFalse();
    assertThat(result.reason()).isEqualTo("DAILY_CAP");
    assertThat(result.newPythoco()).isEqualTo(100);
    assertThat(result.newEnergy()).isEqualTo(50);
    assertThat(result.minutesPassed()).isZero();
  }

  @Test
  void travelRejectsWhenRouteMissing() {
    FastTravelResult result =
        service.travel(TEST_USER_ID, "town_hub", "atlantis", 3, 1_000, 100, 0);

    assertThat(result.success()).isFalse();
    assertThat(result.reason()).isEqualTo("ROUTE_NOT_FOUND");
  }

  @Test
  void travelRejectsRetiredFarmNorthRoute() {
    FastTravelResult result =
        service.travel(TEST_USER_ID, "town_hub", "farm_north", 1, 1_000, 100, 0);

    assertThat(result.success()).isFalse();
    assertThat(result.reason()).isEqualTo("ROUTE_NOT_FOUND");
  }
}
