package com.pythontopia.world.festival;

import static org.assertj.core.api.Assertions.assertThat;

import com.pythontopia.PythontopiaApplication;
import java.util.List;
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
 * Boundary + lifecycle coverage for {@link WorldFestivalService} beyond the happy-path assertions
 * already in {@link WorldFestivalServiceTest}. The original suite covers seed shape, single-day
 * activation, and a single end-of-festival transition; this suite locks down:
 *
 * <ul>
 *   <li>activation isolation across the 11 festival days (one flip never disturbs the others)
 *   <li>{@code pending → active → completed → active} reflip semantics (service is an overwriter,
 *       not a state-machine guard)
 *   <li>{@code onDayEnded} without a prior {@code onDayAdvanced} still flips to {@code completed}
 *   <li>{@code getUpcoming} boundary windows (exact-1, off-by-one before/after, negative/zero,
 *       past-last-festival)
 *   <li>{@code getCurrent} for the first ({@code day_index=1}) and last ({@code day_index=112})
 *       festival days, plus the day-before-last
 *   <li>{@code override_payload} ships {@code null} by default (V118 seed contract)
 * </ul>
 */
@Testcontainers
@SpringBootTest(classes = PythontopiaApplication.class)
class WorldFestivalServiceExtendedTest {

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

  private static final List<Integer> FESTIVAL_DAYS =
      List.of(1, 14, 28, 35, 56, 60, 70, 84, 90, 100, 112);

  @Autowired WorldFestivalRepository repository;
  @Autowired WorldFestivalService service;
  @Autowired JdbcTemplate jdbcTemplate;

  @BeforeEach
  void resetFestivalStatuses() {
    jdbcTemplate.update(
        "UPDATE world_festival SET status = 'pending' WHERE status <> 'pending'");
  }

  @Test
  void onDayAdvancedFlipsOnlyTheTargetedDayLeavingOthersPending() {
    service.onDayAdvanced(35);

    assertThat(repository.findByDay(35).orElseThrow().status()).isEqualTo("active");
    for (int day : FESTIVAL_DAYS) {
      if (day == 35) {
        continue;
      }
      assertThat(repository.findByDay(day).orElseThrow().status())
          .as("Day %d should remain pending while only day 35 was activated", day)
          .isEqualTo("pending");
    }
  }

  @Test
  void allElevenFestivalDaysCanBeActivatedIndependently() {
    for (int day : FESTIVAL_DAYS) {
      service.onDayAdvanced(day);
    }

    for (int day : FESTIVAL_DAYS) {
      assertThat(repository.findByDay(day).orElseThrow().status())
          .as("Day %d should be active after global flip", day)
          .isEqualTo("active");
    }
  }

  @Test
  void onDayAdvancedTwiceForSameDayKeepsActiveStatus() {
    service.onDayAdvanced(56);
    service.onDayAdvanced(56);

    assertThat(repository.findByDay(56).orElseThrow().status()).isEqualTo("active");
  }

  @Test
  void completedFestivalCanBeReFlippedToActiveBecauseServiceIsAnOverwriter() {
    service.onDayAdvanced(60);
    service.onDayEnded(60);
    assertThat(repository.findByDay(60).orElseThrow().status()).isEqualTo("completed");

    service.onDayAdvanced(60);

    assertThat(repository.findByDay(60).orElseThrow().status()).isEqualTo("active");
  }

  @Test
  void onDayEndedWithoutPriorOnDayAdvancedStillFlipsPendingToCompleted() {
    service.onDayEnded(70);

    assertThat(repository.findByDay(70).orElseThrow().status()).isEqualTo("completed");
  }

  @Test
  void onDayEndedOnNonFestivalDayIsNoOpAcrossAllSeededDays() {
    service.onDayEnded(50);
    service.onDayEnded(113);
    service.onDayEnded(0);

    for (int day : FESTIVAL_DAYS) {
      assertThat(repository.findByDay(day).orElseThrow().status())
          .as("Day %d should remain pending after no-op onDayEnded calls", day)
          .isEqualTo("pending");
    }
  }

  @Test
  void getCurrentReturnsLastFestivalOnDay112BoundaryAndEmptyOnDay113() {
    assertThat(service.getCurrent(112))
        .isPresent()
        .get()
        .extracting(WorldFestival::nameKey)
        .isEqualTo("new_year_eve");
    assertThat(service.getCurrent(113)).isEmpty();
  }

  @Test
  void getUpcomingWindowOfOneDayHitsExactNextFestival() {
    var fromDay13 = service.getUpcoming(13, 1);
    var fromDay12 = service.getUpcoming(12, 1);

    assertThat(fromDay13).hasSize(1).extracting(WorldFestival::dayIndex).containsExactly(14);
    assertThat(fromDay12).isEmpty();
  }

  @Test
  void getUpcomingPastFinalFestivalReturnsEmpty() {
    assertThat(service.getUpcoming(112, 50)).isEmpty();
    assertThat(service.getUpcoming(200, 1000)).isEmpty();
  }

  @Test
  void getUpcomingWithNonPositiveWindowReturnsEmptyEvenWhenNextFestivalNearby() {
    assertThat(service.getUpcoming(13, 0)).isEmpty();
    assertThat(service.getUpcoming(13, -5)).isEmpty();
    assertThat(service.getUpcoming(13, Integer.MIN_VALUE)).isEmpty();
  }

  @Test
  void getUpcomingMidYearReturnsExactlyThePostMidtermFestivals() {
    var upcoming = service.getUpcoming(34, 26);

    assertThat(upcoming)
        .extracting(WorldFestival::dayIndex)
        .containsExactly(35, 56, 60);
  }

  @Test
  void v118SeedShipsWithNullOverridePayloadAcrossEveryRow() {
    repository
        .findAll()
        .forEach(
            festival ->
                assertThat(festival.overridePayload())
                    .as("Festival day %d override_payload should default to null", festival.dayIndex())
                    .isNull());
  }
}
