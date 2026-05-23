package com.pythontopia.npc.relationship;

import static org.assertj.core.api.Assertions.assertThat;

import com.pythontopia.PythontopiaApplication;
import java.util.Optional;
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
 * Acceptance tests for {@link NpcRelationshipService} backed by the real V117 migration in a
 * pgvector-on-Postgres-16 Testcontainer. Covers: lazy row creation, favorite/disliked gift deltas,
 * weekly ±10 cap, bidirectional [0,100] clamp, and {@code npc_gift_log} audit.
 */
@Testcontainers
@SpringBootTest(classes = PythontopiaApplication.class)
class NpcRelationshipServiceTest {

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
    // Pin Flyway to V117 (this skeleton's own migration) so the test stays decoupled
    // from later R-12 migrations contributed by parallel feature branches.
    registry.add("spring.flyway.target", () -> "117");
    registry.add(
        "pythontopia.security.jwt.secret",
        () -> "test-secret-must-be-long-enough-for-hs256");
    registry.add("pythontopia.security.teacherInviteCode", () -> "test-teacher-code");
  }

  @Autowired NpcRelationshipService service;
  @Autowired NpcRelationshipRepository repository;
  @Autowired JdbcTemplate jdbcTemplate;

  private long userId;

  @BeforeEach
  void seedUser() {
    jdbcTemplate.update(
        "DELETE FROM npc_gift_log WHERE user_id IN ("
            + "SELECT id FROM users WHERE username LIKE 'npc-rel-%')");
    jdbcTemplate.update(
        "DELETE FROM npc_relationship WHERE user_id IN ("
            + "SELECT id FROM users WHERE username LIKE 'npc-rel-%')");
    jdbcTemplate.update("DELETE FROM users WHERE username LIKE 'npc-rel-%'");

    String username = "npc-rel-" + System.nanoTime();
    jdbcTemplate.update(
        """
        INSERT INTO users (username, display_name, password_hash, role)
        VALUES (?, 'NpcRel Tester', 'hash-not-used', 'STUDENT')
        """,
        username);
    userId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM users WHERE username = ?", Long.class, username);
  }

  @Test
  void newUserStartsAtZeroFriendshipAndIsStranger() {
    int friendship = service.applyFriendshipDelta(userId, "mayor_py", 0, 1);

    assertThat(friendship).isZero();
    Optional<NpcRelationship> row = repository.findByUserAndNpc(userId, "mayor_py");
    assertThat(row).isPresent();
    assertThat(row.get().isStranger()).isTrue();
    assertThat(row.get().isClose()).isFalse();
  }

  @Test
  void favoriteGiftRaisesFriendshipByExpectedDelta() {
    int friendship = service.giveGift(userId, "farmer_kc", "watering_can", 1);

    assertThat(friendship).isEqualTo(8);
    Integer logCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM npc_gift_log WHERE user_id = ? AND npc_key = ?",
            Integer.class,
            userId,
            "farmer_kc");
    assertThat(logCount).isEqualTo(1);
  }

  @Test
  void dislikedGiftLowersFriendship() {
    service.applyFriendshipDelta(userId, "mayor_py", 10, 1);

    int friendship = service.giveGift(userId, "mayor_py", "axe", 1);

    assertThat(friendship).isEqualTo(8);
  }

  @Test
  void weeklyCapStopsFurtherIncreasePastTenPerNpc() {
    int afterFirst = service.giveGift(userId, "farmer_kc", "watering_can", 1);
    int afterSecond = service.giveGift(userId, "farmer_kc", "watering_can", 1);
    int afterThird = service.giveGift(userId, "farmer_kc", "watering_can", 1);

    assertThat(afterFirst).isEqualTo(8);
    assertThat(afterSecond).isEqualTo(10);
    assertThat(afterThird).isEqualTo(10);
  }

  @Test
  void friendshipClampedAtUpperBoundOneHundred() {
    jdbcTemplate.update(
        """
        INSERT INTO npc_relationship (user_id, npc_key, friendship)
        VALUES (?, ?, 95)
        """,
        userId,
        "librarian_doc");

    int friendship = service.applyFriendshipDelta(userId, "librarian_doc", 50, 1);

    assertThat(friendship).isEqualTo(100);
    assertThat(repository.findByUserAndNpc(userId, "librarian_doc"))
        .get()
        .extracting(NpcRelationship::isClose)
        .isEqualTo(true);
  }

  @Test
  void friendshipClampedAtLowerBoundZeroAndNeverGoesNegative() {
    jdbcTemplate.update(
        """
        INSERT INTO npc_relationship (user_id, npc_key, friendship)
        VALUES (?, ?, 5)
        """,
        userId,
        "sage_review");

    int friendship = service.applyFriendshipDelta(userId, "sage_review", -100, 1);

    assertThat(friendship).isZero();
  }

  @Test
  void giveGiftAppendsLogRowWithActualClampedDelta() {
    service.giveGift(userId, "foreman_bug", "pickaxe", 5);
    service.giveGift(userId, "foreman_bug", "pickaxe", 5);
    service.giveGift(userId, "foreman_bug", "pickaxe", 5);

    Integer totalRows =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM npc_gift_log
            WHERE user_id = ? AND npc_key = ? AND item_key = ?
            """,
            Integer.class,
            userId,
            "foreman_bug",
            "pickaxe");
    Integer sumDelta =
        jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(SUM(friendship_delta), 0) FROM npc_gift_log
            WHERE user_id = ? AND npc_key = ?
            """,
            Integer.class,
            userId,
            "foreman_bug");

    assertThat(totalRows).isEqualTo(3);
    assertThat(sumDelta).isEqualTo(10);
  }
}
