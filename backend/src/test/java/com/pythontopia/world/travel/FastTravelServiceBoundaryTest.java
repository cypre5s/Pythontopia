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
 * Boundary-focused companion to {@link FastTravelServiceTest}. The original suite covers the happy
 * path plus a handful of failure reasons; this one nails down the off-by-one boundaries that the
 * fast-travel skeleton's short-circuit ladder is most likely to regress on:
 *
 * <ul>
 *   <li>{@code unlockPhase} versus {@code currentPhase} for routes seeded at phase 1 / 2 / 3
 *       (including the only phase-3 route, {@code campus_east → town_hub} with zero pythoco /
 *       energy / minutes)
 *   <li>{@code currentPythoco} at, just-above, and just-below the {@code costPythoco} bar
 *   <li>{@code currentEnergy} at, just-above, and just-below both the {@code costEnergy} bar and
 *       the {@code MIN_ENERGY_TO_TRAVEL = 10} floor
 *   <li>short-circuit ordering: {@code INSUFFICIENT_FUND} beats {@code INSUFFICIENT_ENERGY},
 *       {@code INSUFFICIENT_ENERGY} beats {@code LOW_ENERGY}
 *   <li>{@code dailyUsedCount} crossing the {@code DAILY_USAGE_CAP = 10} threshold
 *   <li>missing routes: {@code (toScene, fromScene)} reverse, {@code (scene, scene)} self-loop
 *   <li>read-only {@code canTravel} ignores the daily cap entirely while {@link #travel} does not
 * </ul>
 *
 * <p>Mirrors the migration-replay bootstrap of {@link FastTravelServiceTest} so neither suite
 * depends on Flyway-loaded sibling migrations that may break the chain.
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

    String migration =
        Files.readString(
            Paths.get("src/main/resources/db/migration/V119__fast_travel_route.sql"),
            StandardCharsets.UTF_8);
    runScript(migration);

    repository = new FastTravelRepository(jdbcTemplate);
    service = new FastTravelService(repository);
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

  // ---------- canTravel pythoco / energy boundaries ----------

  @Test
  void canTravelAcceptsPythocoAtExactCost() {
    assertThat(service.canTravel(TEST_USER_ID, "town_hub", "farm_north", 1, 10, 50)).isTrue();
  }

  @Test
  void canTravelRejectsPythocoOneUnitBelowCost() {
    assertThat(service.canTravel(TEST_USER_ID, "town_hub", "farm_north", 1, 9, 50)).isFalse();
  }

  @Test
  void canTravelAcceptsEnergyAtExactMinimumFloor() {
    assertThat(service.canTravel(TEST_USER_ID, "town_hub", "farm_north", 1, 100, 10)).isTrue();
  }

  @Test
  void canTravelRejectsEnergyOneUnitBelowMinimumFloor() {
    assertThat(service.canTravel(TEST_USER_ID, "town_hub", "farm_north", 1, 100, 9)).isFalse();
  }

  @Test
  void canTravelRejectsZeroCostRouteWhenEnergyBelowFloor() {
    assertThat(service.canTravel(TEST_USER_ID, "port_west", "farm_north", 2, 100, 9)).isFalse();
  }

  @Test
  void canTravelAcceptsZeroCostRouteWhenEnergyAtFloor() {
    assertThat(service.canTravel(TEST_USER_ID, "port_west", "farm_north", 2, 100, 10)).isTrue();
  }

  // ---------- canTravel phase combinations ----------

  @Test
  void canTravelRejectsAllPhaseOneRoutesAtPhaseZero() {
    assertThat(service.canTravel(TEST_USER_ID, "town_hub", "farm_north", 0, 1_000, 100)).isFalse();
    assertThat(service.canTravel(TEST_USER_ID, "town_hub", "mine_south", 0, 1_000, 100)).isFalse();
    assertThat(service.canTravel(TEST_USER_ID, "town_hub", "campus_east", 0, 1_000, 100))
        .isFalse();
    assertThat(service.canTravel(TEST_USER_ID, "town_hub", "port_west", 0, 1_000, 100)).isFalse();
  }

  @Test
  void canTravelAcceptsAllPhaseOneRoutesAtPhaseOne() {
    assertThat(service.canTravel(TEST_USER_ID, "town_hub", "farm_north", 1, 1_000, 100)).isTrue();
    assertThat(service.canTravel(TEST_USER_ID, "town_hub", "mine_south", 1, 1_000, 100)).isTrue();
    assertThat(service.canTravel(TEST_USER_ID, "town_hub", "campus_east", 1, 1_000, 100))
        .isTrue();
    assertThat(service.canTravel(TEST_USER_ID, "town_hub", "port_west", 1, 1_000, 100)).isTrue();
  }

  @Test
  void canTravelRejectsPortWestCrossRoutesAtPhaseOneAndAcceptsAtPhaseTwo() {
    assertThat(service.canTravel(TEST_USER_ID, "port_west", "farm_north", 1, 100, 50)).isFalse();
    assertThat(service.canTravel(TEST_USER_ID, "port_west", "mine_south", 1, 100, 50)).isFalse();
    assertThat(service.canTravel(TEST_USER_ID, "port_west", "farm_north", 2, 100, 50)).isTrue();
    assertThat(service.canTravel(TEST_USER_ID, "port_west", "mine_south", 2, 100, 50)).isTrue();
  }

  @Test
  void canTravelRejectsCampusSecretRouteUntilPhaseThree() {
    assertThat(service.canTravel(TEST_USER_ID, "campus_east", "town_hub", 1, 100, 50)).isFalse();
    assertThat(service.canTravel(TEST_USER_ID, "campus_east", "town_hub", 2, 100, 50)).isFalse();
    assertThat(service.canTravel(TEST_USER_ID, "campus_east", "town_hub", 3, 0, 10)).isTrue();
  }

  // ---------- missing routes ----------

  @Test
  void canTravelRejectsReverseOfPhaseTwoRouteWhenNotSeeded() {
    // (farm_north, port_west) is NOT seeded even though (port_west, farm_north) is.
    assertThat(service.canTravel(TEST_USER_ID, "farm_north", "port_west", 3, 10_000, 100))
        .isFalse();
  }

  @Test
  void canTravelRejectsSelfLoopAsRouteNotFound() {
    assertThat(service.canTravel(TEST_USER_ID, "town_hub", "town_hub", 3, 10_000, 100)).isFalse();
  }

  // ---------- travel short-circuit ordering ----------

  @Test
  void travelReturnsInsufficientFundBeforeInsufficientEnergyWhenBothFail() {
    FastTravelResult result =
        service.travel(TEST_USER_ID, "town_hub", "farm_north", 1, 0, 0, 0);

    assertThat(result.success()).isFalse();
    assertThat(result.reason()).isEqualTo("INSUFFICIENT_FUND");
  }

  @Test
  void travelReturnsInsufficientEnergyBeforeLowEnergyWhenCostExceedsCurrent() {
    FastTravelResult result =
        service.travel(TEST_USER_ID, "town_hub", "farm_north", 1, 100, 4, 0);

    assertThat(result.success()).isFalse();
    assertThat(result.reason()).isEqualTo("INSUFFICIENT_ENERGY");
  }

  @Test
  void travelReturnsLowEnergyWhenCostMetButFloorMissed() {
    FastTravelResult result =
        service.travel(TEST_USER_ID, "town_hub", "farm_north", 1, 100, 5, 0);

    assertThat(result.success()).isFalse();
    assertThat(result.reason()).isEqualTo("LOW_ENERGY");
  }

  // ---------- daily cap boundary ----------

  @Test
  void travelAcceptsDailyUsedNineAndRejectsAtTen() {
    FastTravelResult nineAcceptable =
        service.travel(TEST_USER_ID, "town_hub", "farm_north", 1, 100, 50, 9);
    FastTravelResult tenRejected =
        service.travel(TEST_USER_ID, "town_hub", "farm_north", 1, 100, 50, 10);

    assertThat(nineAcceptable.success()).isTrue();
    assertThat(nineAcceptable.reason()).isEqualTo("OK");
    assertThat(tenRejected.success()).isFalse();
    assertThat(tenRejected.reason()).isEqualTo("DAILY_CAP");
  }

  @Test
  void travelClampsDailyCapBeyondTen() {
    FastTravelResult result =
        service.travel(TEST_USER_ID, "town_hub", "farm_north", 1, 100, 50, Integer.MAX_VALUE);

    assertThat(result.success()).isFalse();
    assertThat(result.reason()).isEqualTo("DAILY_CAP");
    assertThat(result.newPythoco()).isEqualTo(100);
    assertThat(result.newEnergy()).isEqualTo(50);
    assertThat(result.minutesPassed()).isZero();
  }

  // ---------- canTravel ignores daily cap ----------

  @Test
  void canTravelIgnoresDailyCapAndStaysTrueEvenWhenTravelWouldReject() {
    assertThat(service.canTravel(TEST_USER_ID, "town_hub", "farm_north", 1, 100, 50)).isTrue();
    FastTravelResult travelResult =
        service.travel(TEST_USER_ID, "town_hub", "farm_north", 1, 100, 50, 10);

    assertThat(travelResult.success()).isFalse();
    assertThat(travelResult.reason()).isEqualTo("DAILY_CAP");
  }

  // ---------- travel successful zero-cost path ----------

  @Test
  void travelSucceedsOnZeroCostCampusSecretRouteAtPhaseThree() {
    FastTravelResult result =
        service.travel(TEST_USER_ID, "campus_east", "town_hub", 3, 0, 10, 0);

    assertThat(result.success()).isTrue();
    assertThat(result.reason()).isEqualTo("OK");
    assertThat(result.newPythoco()).isZero();
    assertThat(result.newEnergy()).isEqualTo(10);
    assertThat(result.minutesPassed()).isZero();
  }

  // ---------- exact match success ----------

  @Test
  void travelSucceedsWhenPythocoAndEnergyExactlyMatchRouteCosts() {
    FastTravelResult result =
        service.travel(TEST_USER_ID, "town_hub", "mine_south", 1, 10, 10, 0);

    assertThat(result.success()).isTrue();
    assertThat(result.reason()).isEqualTo("OK");
    assertThat(result.newPythoco()).isZero();
    assertThat(result.newEnergy()).isEqualTo(5);
    assertThat(result.minutesPassed()).isEqualTo(60);
  }

  // ---------- repository view of phase unlock ----------

  @Test
  void findUnlockedFromTownHubGrowsMonotonicallyAcrossPhases() {
    var phase0 = repository.findUnlockedFromScene("town_hub", 0);
    var phase1 = repository.findUnlockedFromScene("town_hub", 1);
    var phase3 = repository.findUnlockedFromScene("town_hub", 3);

    assertThat(phase0).isEmpty();
    assertThat(phase1)
        .extracting(FastTravelRoute::toScene)
        .containsExactlyInAnyOrder("farm_north", "mine_south", "campus_east", "port_west");
    assertThat(phase3).containsExactlyInAnyOrderElementsOf(phase1);
  }
}
