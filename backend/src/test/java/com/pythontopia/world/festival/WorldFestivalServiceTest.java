package com.pythontopia.world.festival;

import static org.assertj.core.api.Assertions.assertThat;

import com.pythontopia.PythontopiaApplication;
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
 * Integration test for V118 + {@link WorldFestivalService}. Uses a real PostgreSQL container so
 * the migration's seed insert + status check constraint are exercised end-to-end. Each test
 * resets every {@code world_festival.status} back to {@code 'pending'} so the assertions are
 * independent of execution order.
 */
@Testcontainers
@SpringBootTest(classes = PythontopiaApplication.class)
class WorldFestivalServiceTest {

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

  @Autowired WorldFestivalRepository repository;
  @Autowired WorldFestivalService service;
  @Autowired JdbcTemplate jdbcTemplate;

  @BeforeEach
  void resetFestivalStatuses() {
    jdbcTemplate.update(
        "UPDATE world_festival SET status = 'pending' WHERE status <> 'pending'");
  }

  @Test
  void v118SeedsExactlyElevenFestivalsInDesignOrder() {
    var all = repository.findAll();

    assertThat(all).hasSize(11);
    assertThat(all)
        .extracting(WorldFestival::dayIndex)
        .containsExactly(1, 14, 28, 35, 56, 60, 70, 84, 90, 100, 112);
    assertThat(all)
        .extracting(WorldFestival::nameKey)
        .containsExactly(
            "spring_opening",
            "spring_quiz",
            "spring_finale_race",
            "midterm_festival",
            "port_fair",
            "bug_carnival",
            "final_warmup",
            "finals_festival",
            "winter_showcase",
            "silent_night",
            "new_year_eve");
    assertThat(all).allSatisfy(f -> assertThat(f.status()).isEqualTo("pending"));
    assertThat(all).allSatisfy(f -> assertThat(f.id()).isNotNull().isPositive());
    assertThat(all).allSatisfy(f -> assertThat(f.createdAt()).isNotNull());
  }

  @Test
  void getCurrentReturnsDay1SpringOpening() {
    var festival = service.getCurrent(1);

    assertThat(festival).isPresent();
    assertThat(festival.get().nameKey()).isEqualTo("spring_opening");
    assertThat(festival.get().dayIndex()).isEqualTo(1);
    assertThat(festival.get().status()).isEqualTo("pending");
  }

  @Test
  void getCurrentReturnsEmptyForNonFestivalDays() {
    assertThat(service.getCurrent(2)).isEmpty();
    assertThat(service.getCurrent(50)).isEmpty();
    assertThat(service.getCurrent(113)).isEmpty();
  }

  @Test
  void getUpcomingIncludesDay14WhenQueriedFromDay13() {
    var upcoming = service.getUpcoming(13, 5);

    assertThat(upcoming).hasSize(1);
    assertThat(upcoming.get(0).dayIndex()).isEqualTo(14);
    assertThat(upcoming.get(0).nameKey()).isEqualTo("spring_quiz");
  }

  @Test
  void getUpcomingWithWideWindowReturnsAllForwardFestivals() {
    var upcoming = service.getUpcoming(0, 200);

    assertThat(upcoming).hasSize(11);
    assertThat(upcoming)
        .extracting(WorldFestival::dayIndex)
        .containsExactly(1, 14, 28, 35, 56, 60, 70, 84, 90, 100, 112);
  }

  @Test
  void getUpcomingExcludesCurrentDayItself() {
    var upcoming = service.getUpcoming(14, 5);

    assertThat(upcoming).isEmpty();
  }

  @Test
  void onDayAdvancedFlipsFestivalStatusToActive() {
    service.onDayAdvanced(14);

    var festival = repository.findByDay(14).orElseThrow();
    assertThat(festival.status()).isEqualTo("active");
  }

  @Test
  void onDayAdvancedOnNonFestivalDayIsNoOp() {
    service.onDayAdvanced(2);

    var day1 = repository.findByDay(1).orElseThrow();
    var day14 = repository.findByDay(14).orElseThrow();
    assertThat(day1.status()).isEqualTo("pending");
    assertThat(day14.status()).isEqualTo("pending");
  }

  @Test
  void onDayEndedFlipsFestivalStatusToCompleted() {
    service.onDayAdvanced(28);
    service.onDayEnded(28);

    var festival = repository.findByDay(28).orElseThrow();
    assertThat(festival.status()).isEqualTo("completed");
  }
}
