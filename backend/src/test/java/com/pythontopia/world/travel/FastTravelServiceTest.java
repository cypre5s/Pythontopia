package com.pythontopia.world.travel;

import static org.assertj.core.api.Assertions.assertThat;

import com.pythontopia.world.travel.FastTravelService.FastTravelResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * Isolated JDBC test for the R-12 W5 fast travel skeleton. We deliberately bypass
 * {@code @SpringBootTest} so this suite is independent of unrelated migrations that may break the
 * full Flyway chain (e.g. V118 ships outside our scope). The V119 SQL is loaded directly from the
 * classpath and executed by hand, which still covers <em>both</em> the table DDL and the 10-row
 * seed insert end-to-end against a real Postgres image.
 */
@Testcontainers
class FastTravelServiceTest {

  private static final long TEST_USER_ID = 1L;

  // Strip line comments so the splitter below does not break on `--` inside the SQL file.
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
            Paths.get(
                "src/main/resources/db/migration/V119__fast_travel_route.sql"),
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

  // ---------- repository / seed ----------

  @Test
  void v119SeedExposesAtLeastTenRoutes() {
    assertThat(repository.findAll()).hasSizeGreaterThanOrEqualTo(10);
  }

  @Test
  void findReturnsKnownTownHubToFarmNorthRoute() {
    FastTravelRoute route = repository.find("town_hub", "farm_north").orElseThrow();

    assertThat(route.fromScene()).isEqualTo("town_hub");
    assertThat(route.toScene()).isEqualTo("farm_north");
    assertThat(route.unlockPhase()).isEqualTo(1);
    assertThat(route.costPythoco()).isEqualTo(10);
    assertThat(route.costEnergy()).isEqualTo(5);
    assertThat(route.costMinutes()).isEqualTo(60);
  }

  @Test
  void findUnlockedFromSceneHidesHigherPhaseRoutes() {
    var phase1 = repository.findUnlockedFromScene("port_west", 1);
    var phase2 = repository.findUnlockedFromScene("port_west", 2);

    assertThat(phase1).extracting(FastTravelRoute::toScene).containsExactly("town_hub");
    assertThat(phase2)
        .extracting(FastTravelRoute::toScene)
        .containsExactlyInAnyOrder("town_hub", "farm_north", "mine_south");
  }

  // ---------- canTravel ----------

  @Test
  void canTravelAcceptsTownHubToFarmNorthAtPhase1() {
    assertThat(service.canTravel(TEST_USER_ID, "town_hub", "farm_north", 1, 100, 50)).isTrue();
  }

  @Test
  void canTravelRejectsTownHubToFarmNorthAtPhase0() {
    assertThat(service.canTravel(TEST_USER_ID, "town_hub", "farm_north", 0, 100, 50)).isFalse();
  }

  @Test
  void canTravelRejectsWhenPythocoBelowCost() {
    assertThat(service.canTravel(TEST_USER_ID, "town_hub", "farm_north", 1, 5, 50)).isFalse();
  }

  @Test
  void canTravelRejectsWhenEnergyBelowFloor() {
    assertThat(service.canTravel(TEST_USER_ID, "town_hub", "farm_north", 1, 100, 9)).isFalse();
  }

  // ---------- travel ----------

  @Test
  void travelDeductsCostsOnSuccess() {
    FastTravelResult result =
        service.travel(TEST_USER_ID, "town_hub", "farm_north", 1, 100, 50, 0);

    assertThat(result.success()).isTrue();
    assertThat(result.reason()).isEqualTo("OK");
    assertThat(result.newPythoco()).isEqualTo(90);
    assertThat(result.newEnergy()).isEqualTo(45);
    assertThat(result.minutesPassed()).isEqualTo(60);
  }

  @Test
  void travelRejectsAtDailyCapTen() {
    FastTravelResult result =
        service.travel(TEST_USER_ID, "town_hub", "farm_north", 1, 100, 50, 10);

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
    assertThat(result.newPythoco()).isEqualTo(1_000);
    assertThat(result.newEnergy()).isEqualTo(100);
  }

  @Test
  void travelRejectsWhenPhaseLocked() {
    FastTravelResult result =
        service.travel(TEST_USER_ID, "port_west", "farm_north", 1, 1_000, 100, 0);

    assertThat(result.success()).isFalse();
    assertThat(result.reason()).isEqualTo("PHASE_LOCKED");
  }
}
