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
 * Boundary tests for R-14 shrunk fast travel ({@code town_hub ↔ mine_south} only).
 */
@Testcontainers
class FastTravelServiceBoundaryTest {

  private static final long TEST_USER_ID = 7L;

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
  void canTravelAcceptsPythocoAtExactCost() {
    assertThat(service.canTravel(TEST_USER_ID, "town_hub", "mine_south", 1, 10, 50)).isTrue();
  }

  @Test
  void canTravelRejectsPythocoOneUnitBelowCost() {
    assertThat(service.canTravel(TEST_USER_ID, "town_hub", "mine_south", 1, 9, 50)).isFalse();
  }

  @Test
  void canTravelAcceptsEnergyAtExactMinimumFloor() {
    assertThat(service.canTravel(TEST_USER_ID, "town_hub", "mine_south", 1, 100, 10)).isTrue();
  }

  @Test
  void canTravelRejectsEnergyOneUnitBelowMinimumFloor() {
    assertThat(service.canTravel(TEST_USER_ID, "town_hub", "mine_south", 1, 100, 9)).isFalse();
  }

  @Test
  void canTravelRejectsHubMineAtPhaseZero() {
    assertThat(service.canTravel(TEST_USER_ID, "town_hub", "mine_south", 0, 1_000, 100)).isFalse();
    assertThat(service.canTravel(TEST_USER_ID, "mine_south", "town_hub", 0, 1_000, 100)).isFalse();
  }

  @Test
  void canTravelAcceptsHubMineAtPhaseOne() {
    assertThat(service.canTravel(TEST_USER_ID, "town_hub", "mine_south", 1, 1_000, 100)).isTrue();
    assertThat(service.canTravel(TEST_USER_ID, "mine_south", "town_hub", 1, 1_000, 100)).isTrue();
  }

  @Test
  void canTravelRejectsSelfLoopAsRouteNotFound() {
    assertThat(service.canTravel(TEST_USER_ID, "town_hub", "town_hub", 3, 10_000, 100)).isFalse();
  }

  @Test
  void canTravelRejectsRetiredOuterMapRoutes() {
    assertThat(service.canTravel(TEST_USER_ID, "town_hub", "farm_north", 1, 1_000, 100)).isFalse();
    assertThat(service.canTravel(TEST_USER_ID, "port_west", "mine_south", 2, 1_000, 100)).isFalse();
  }

  @Test
  void travelReturnsInsufficientFundBeforeInsufficientEnergyWhenBothFail() {
    FastTravelResult result =
        service.travel(TEST_USER_ID, "town_hub", "mine_south", 1, 0, 0, 0);

    assertThat(result.success()).isFalse();
    assertThat(result.reason()).isEqualTo("INSUFFICIENT_FUND");
  }

  @Test
  void travelReturnsInsufficientEnergyBeforeLowEnergyWhenCostExceedsCurrent() {
    FastTravelResult result =
        service.travel(TEST_USER_ID, "town_hub", "mine_south", 1, 100, 4, 0);

    assertThat(result.success()).isFalse();
    assertThat(result.reason()).isEqualTo("INSUFFICIENT_ENERGY");
  }

  @Test
  void travelReturnsLowEnergyWhenCostMetButFloorMissed() {
    FastTravelResult result =
        service.travel(TEST_USER_ID, "town_hub", "mine_south", 1, 100, 5, 0);

    assertThat(result.success()).isFalse();
    assertThat(result.reason()).isEqualTo("LOW_ENERGY");
  }

  @Test
  void travelAcceptsDailyUsedNineAndRejectsAtTen() {
    FastTravelResult nineAcceptable =
        service.travel(TEST_USER_ID, "town_hub", "mine_south", 1, 100, 50, 9);
    FastTravelResult tenRejected =
        service.travel(TEST_USER_ID, "town_hub", "mine_south", 1, 100, 50, 10);

    assertThat(nineAcceptable.success()).isTrue();
    assertThat(tenRejected.success()).isFalse();
    assertThat(tenRejected.reason()).isEqualTo("DAILY_CAP");
  }

  @Test
  void canTravelIgnoresDailyCapWhileTravelEnforcesIt() {
    assertThat(service.canTravel(TEST_USER_ID, "town_hub", "mine_south", 1, 100, 50)).isTrue();
    FastTravelResult travelResult =
        service.travel(TEST_USER_ID, "town_hub", "mine_south", 1, 100, 50, 10);

    assertThat(travelResult.success()).isFalse();
    assertThat(travelResult.reason()).isEqualTo("DAILY_CAP");
  }

  @Test
  void travelSucceedsWhenPythocoAndEnergyExactlyMatchRouteCosts() {
    FastTravelResult result =
        service.travel(TEST_USER_ID, "town_hub", "mine_south", 1, 10, 10, 0);

    assertThat(result.success()).isTrue();
    assertThat(result.newPythoco()).isZero();
    assertThat(result.newEnergy()).isEqualTo(5);
    assertThat(result.minutesPassed()).isEqualTo(60);
  }

  @Test
  void findUnlockedFromTownHubOnlyMineSouthAfterR14Shrink() {
    assertThat(repository.findUnlockedFromScene("town_hub", 1))
        .extracting(FastTravelRoute::toScene)
        .containsExactly("mine_south");
  }
}
