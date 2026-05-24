package com.pythontopia.npc.relationship;

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
 * Extended acceptance tests for {@link NpcRelationshipService} that go beyond the single-NPC,
 * single-user happy paths already in {@link NpcRelationshipServiceTest}. Focus areas:
 *
 * <ul>
 *   <li>weekly ±10 cap isolation across <em>different NPCs</em> for the same user
 *   <li>weekly cap isolation across <em>different users</em> for the same NPC
 *   <li>{@code npc_gift_log.gift_tier} inference for every reachable tier
 *       (love / like / neutral / dislike) once weekly clamping kicks in
 *   <li>negative-direction cap: 5 dislike gifts walk friendship down by 10, the 6th gets clamped
 *       to a zero-delta neutral row
 *   <li>unknown {@code itemKey} / unknown {@code npcKey} fall back to {@link
 *       NpcRelationshipService#NEUTRAL_GIFT_DELTA}=1
 *   <li>{@code last_gift_day} mirrors the {@code currentDay} parameter on every applied delta,
 *       even when the delta is clamped to zero
 *   <li>{@link NpcRelationshipRepository#findAllByUser} returns rows ordered alphabetically by
 *       {@code npc_key}
 *   <li>friendship tier predicates cover the five canonical bands from the design doc
 * </ul>
 *
 * <p>Uses the same Flyway-target pin as the original test so this suite stays decoupled from later
 * R-12 migrations contributed by parallel branches.
 */
@Testcontainers
@SpringBootTest(classes = PythontopiaApplication.class)
class NpcRelationshipServiceExtendedTest {

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
    registry.add("spring.flyway.target", () -> "117");
    registry.add(
        "pythontopia.security.jwt.secret",
        () -> "test-secret-must-be-long-enough-for-hs256");
    registry.add("pythontopia.security.teacherInviteCode", () -> "test-teacher-code");
  }

  @Autowired NpcRelationshipService service;
  @Autowired NpcRelationshipRepository repository;
  @Autowired JdbcTemplate jdbcTemplate;

  private long primaryUserId;
  private long secondaryUserId;

  @BeforeEach
  void seedTwoUsers() {
    jdbcTemplate.update(
        "DELETE FROM npc_gift_log WHERE user_id IN ("
            + "SELECT id FROM users WHERE username LIKE 'npc-rel-ext-%')");
    jdbcTemplate.update(
        "DELETE FROM npc_relationship WHERE user_id IN ("
            + "SELECT id FROM users WHERE username LIKE 'npc-rel-ext-%')");
    jdbcTemplate.update("DELETE FROM users WHERE username LIKE 'npc-rel-ext-%'");

    primaryUserId = insertUser("npc-rel-ext-primary-" + System.nanoTime());
    secondaryUserId = insertUser("npc-rel-ext-secondary-" + System.nanoTime());
  }

  private long insertUser(String username) {
    jdbcTemplate.update(
        """
        INSERT INTO users (username, display_name, password_hash, role)
        VALUES (?, 'NpcRel Ext Tester', 'hash-not-used', 'STUDENT')
        """,
        username);
    return jdbcTemplate.queryForObject(
        "SELECT id FROM users WHERE username = ?", Long.class, username);
  }

  private int countLogRowsByTier(long userId, String npcKey, String giftTier) {
    Integer count =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM npc_gift_log
            WHERE user_id = ? AND npc_key = ? AND gift_tier = ?
            """,
            Integer.class,
            userId,
            npcKey,
            giftTier);
    return count == null ? 0 : count;
  }

  @Test
  void weeklyCapOnFarmerDoesNotAffectMayorForSameUser() {
    service.giveGift(primaryUserId, "farmer_kc", "watering_can", 1);
    service.giveGift(primaryUserId, "farmer_kc", "watering_can", 1);
    service.giveGift(primaryUserId, "farmer_kc", "watering_can", 1);

    int mayorFriendship =
        service.giveGift(primaryUserId, "mayor_py", "coffee", 1);

    assertThat(mayorFriendship).isEqualTo(8);
    assertThat(repository.findByUserAndNpc(primaryUserId, "farmer_kc").orElseThrow().friendship())
        .isEqualTo(10);
  }

  @Test
  void weeklyCapOnUserAhDoesNotAffectUserB() {
    service.giveGift(primaryUserId, "farmer_kc", "watering_can", 1);
    service.giveGift(primaryUserId, "farmer_kc", "watering_can", 1);
    service.giveGift(primaryUserId, "farmer_kc", "watering_can", 1);

    int secondaryFriendship =
        service.giveGift(secondaryUserId, "farmer_kc", "watering_can", 1);
    int secondaryFriendshipSecond =
        service.giveGift(secondaryUserId, "farmer_kc", "watering_can", 1);

    assertThat(secondaryFriendship).isEqualTo(8);
    assertThat(secondaryFriendshipSecond).isEqualTo(10);
    assertThat(
            repository.findByUserAndNpc(primaryUserId, "farmer_kc").orElseThrow().friendship())
        .isEqualTo(10);
  }

  @Test
  void likeTierGiftsLogsLikeRowsThenNeutralRowsOnceCapped() {
    service.giveGift(primaryUserId, "mayor_py", "bread", 1);
    service.giveGift(primaryUserId, "mayor_py", "bread", 1);
    service.giveGift(primaryUserId, "mayor_py", "bread", 1);
    int finalFriendship = service.giveGift(primaryUserId, "mayor_py", "bread", 1);

    assertThat(finalFriendship).isEqualTo(10);
    assertThat(countLogRowsByTier(primaryUserId, "mayor_py", "like")).isEqualTo(2);
    assertThat(countLogRowsByTier(primaryUserId, "mayor_py", "neutral")).isEqualTo(2);
    assertThat(countLogRowsByTier(primaryUserId, "mayor_py", "love")).isZero();
    assertThat(countLogRowsByTier(primaryUserId, "mayor_py", "dislike")).isZero();
  }

  @Test
  void dislikeGiftLogsDislikeTierWhenPriorFriendshipHigh() {
    jdbcTemplate.update(
        """
        INSERT INTO npc_relationship (user_id, npc_key, friendship)
        VALUES (?, 'farmer_kc', 50)
        """,
        primaryUserId);

    int firstAxe = service.giveGift(primaryUserId, "farmer_kc", "axe", 1);
    int secondAxe = service.giveGift(primaryUserId, "farmer_kc", "axe", 1);

    assertThat(firstAxe).isEqualTo(48);
    assertThat(secondAxe).isEqualTo(46);
    assertThat(countLogRowsByTier(primaryUserId, "farmer_kc", "dislike")).isEqualTo(2);
    assertThat(countLogRowsByTier(primaryUserId, "farmer_kc", "neutral")).isZero();
  }

  @Test
  void negativeWeeklyCapStopsFurtherDecreaseAtMinusTenLogsNeutralOnTheSixth() {
    jdbcTemplate.update(
        """
        INSERT INTO npc_relationship (user_id, npc_key, friendship)
        VALUES (?, 'farmer_kc', 50)
        """,
        primaryUserId);

    for (int i = 0; i < 5; i++) {
      service.giveGift(primaryUserId, "farmer_kc", "axe", 1);
    }
    int afterSixth = service.giveGift(primaryUserId, "farmer_kc", "axe", 1);

    assertThat(afterSixth).isEqualTo(40);
    assertThat(countLogRowsByTier(primaryUserId, "farmer_kc", "dislike")).isEqualTo(5);
    assertThat(countLogRowsByTier(primaryUserId, "farmer_kc", "neutral")).isEqualTo(1);
  }

  @Test
  void lastGiftDayMirrorsCurrentDayParameterEvenWhenDeltaIsZero() {
    service.applyFriendshipDelta(primaryUserId, "librarian_doc", 0, 42);

    NpcRelationship row =
        repository.findByUserAndNpc(primaryUserId, "librarian_doc").orElseThrow();

    assertThat(row.lastGiftDay()).isEqualTo(42);
    assertThat(row.friendship()).isZero();
  }

  @Test
  void unknownItemKeyFallsBackToNeutralOnePointDeltaAndNeutralTier() {
    int friendship = service.giveGift(primaryUserId, "mayor_py", "mystery_box", 1);

    assertThat(friendship).isEqualTo(1);
    assertThat(countLogRowsByTier(primaryUserId, "mayor_py", "neutral")).isEqualTo(1);
  }

  @Test
  void unknownNpcKeyFallsBackToNeutralOnePointDelta() {
    int friendship = service.giveGift(primaryUserId, "blacksmith_x", "watering_can", 1);

    assertThat(friendship).isEqualTo(1);
    assertThat(repository.findByUserAndNpc(primaryUserId, "blacksmith_x"))
        .isPresent()
        .get()
        .extracting(NpcRelationship::friendship)
        .isEqualTo(1);
    assertThat(countLogRowsByTier(primaryUserId, "blacksmith_x", "neutral")).isEqualTo(1);
  }

  @Test
  void findAllByUserReturnsRowsAlphabeticalByNpcKey() {
    service.applyFriendshipDelta(primaryUserId, "sage_review", 5, 1);
    service.applyFriendshipDelta(primaryUserId, "mayor_py", 5, 1);
    service.applyFriendshipDelta(primaryUserId, "foreman_bug", 5, 1);
    service.applyFriendshipDelta(primaryUserId, "farmer_kc", 5, 1);
    service.applyFriendshipDelta(primaryUserId, "librarian_doc", 5, 1);

    List<NpcRelationship> rows = repository.findAllByUser(primaryUserId);

    assertThat(rows)
        .extracting(NpcRelationship::npcKey)
        .containsExactly("farmer_kc", "foreman_bug", "librarian_doc", "mayor_py", "sage_review");
  }

  @Test
  void friendshipTierPredicatesCoverAllFiveCanonicalBands() {
    insertRowWithFriendship("npc_stranger", 0);
    insertRowWithFriendship("npc_acquaintance", 30);
    insertRowWithFriendship("npc_friendly", 50);
    insertRowWithFriendship("npc_trusting", 75);
    insertRowWithFriendship("npc_close", 95);

    NpcRelationship stranger =
        repository.findByUserAndNpc(primaryUserId, "npc_stranger").orElseThrow();
    NpcRelationship acquaintance =
        repository.findByUserAndNpc(primaryUserId, "npc_acquaintance").orElseThrow();
    NpcRelationship friendly =
        repository.findByUserAndNpc(primaryUserId, "npc_friendly").orElseThrow();
    NpcRelationship trusting =
        repository.findByUserAndNpc(primaryUserId, "npc_trusting").orElseThrow();
    NpcRelationship close =
        repository.findByUserAndNpc(primaryUserId, "npc_close").orElseThrow();

    assertThat(stranger.isStranger()).isTrue();
    assertThat(acquaintance.isAcquaintance()).isTrue();
    assertThat(friendly.isFriendly()).isTrue();
    assertThat(trusting.isTrusting()).isTrue();
    assertThat(close.isClose()).isTrue();
    assertThat(stranger.isClose()).isFalse();
    assertThat(close.isStranger()).isFalse();
  }

  private void insertRowWithFriendship(String npcKey, int friendship) {
    jdbcTemplate.update(
        """
        INSERT INTO npc_relationship (user_id, npc_key, friendship)
        VALUES (?, ?, ?)
        """,
        primaryUserId,
        npcKey,
        friendship);
  }
}
