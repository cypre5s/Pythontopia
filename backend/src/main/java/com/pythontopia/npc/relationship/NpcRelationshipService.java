package com.pythontopia.npc.relationship;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Friendship state machine driver for the R-12 NPC relationship subsystem.
 *
 * <p>Two public entry points exist:
 *
 * <ul>
 *   <li>{@link #applyFriendshipDelta(long, String, int, int)} — generic friendship adjustment used
 *       by gift, quest, festival and dialog flows. Enforces the ±10 weekly cap (queried from
 *       {@link NpcRelationshipRepository#sumWeeklyDelta(long, String, int)}) and clamps the
 *       persisted value into {@code [0, 100]}.
 *   <li>{@link #giveGift(long, String, String, int)} — wraps applyFriendshipDelta with the gift
 *       affinity lookup and appends an immutable row to {@code npc_gift_log}.
 * </ul>
 *
 * <p>{@link #GIFT_AFFINITY} is a deliberately small (5 NPC × 3 item) skeleton sourced from
 * {@code docs/gameplay/r12-deepen/06-gift-table.md §3} so this skeleton can be exercised end-to-end
 * by tests without depending on the full 20×9 catalog. The production wiring will pull the matrix
 * from {@code item_catalog.gift_tier_map} once the deep-link is opened; until then any
 * {@code (npcKey, itemKey)} pair not present here yields a {@code +1} neutral delta.
 */
@Service
public class NpcRelationshipService {

  static final int WEEKLY_DELTA_CAP = 10;
  static final int FRIENDSHIP_MIN = 0;
  static final int FRIENDSHIP_MAX = 100;
  static final int NEUTRAL_GIFT_DELTA = 1;

  static final Map<String, Map<String, Integer>> GIFT_AFFINITY = buildAffinity();

  private static Map<String, Map<String, Integer>> buildAffinity() {
    Map<String, Map<String, Integer>> map = new HashMap<>();
    map.put("mayor_py", Map.of("coffee", 8, "bread", 4, "axe", -2));
    map.put("farmer_kc", Map.of("watering_can", 8, "hoe", 4, "axe", -2));
    map.put("foreman_bug", Map.of("pickaxe", 8, "bug_jar", 8, "flower", -2));
    map.put("librarian_doc", Map.of("coffee", 8, "flower", 4, "axe", -2));
    map.put("sage_review", Map.of("flower", 8, "bread", 4, "pickaxe", -2));
    return Map.copyOf(map);
  }

  private final NpcRelationshipRepository repository;

  public NpcRelationshipService(NpcRelationshipRepository repository) {
    this.repository = repository;
  }

  /**
   * Apply a signed friendship adjustment and return the new clamped friendship value. Lazily
   * creates the relationship row when missing so callers never have to seed it.
   */
  @Transactional
  public int applyFriendshipDelta(long userId, String npcKey, int delta, int currentDay) {
    NpcRelationship current =
        repository
            .findByUserAndNpc(userId, npcKey)
            .orElseGet(() -> repository.upsert(userId, npcKey, 0, null, 0));

    int historicalSum = repository.sumWeeklyDelta(userId, npcKey, currentDay);
    int clampedDelta = clampWeeklyDelta(historicalSum, delta);
    int newFriendship = clampFriendship(current.friendship() + clampedDelta);
    int appliedDelta = newFriendship - current.friendship();
    int newWeeklySum = historicalSum + appliedDelta;

    repository.upsert(userId, npcKey, newFriendship, currentDay, newWeeklySum);
    return newFriendship;
  }

  /**
   * Look up the affinity delta for {@code (npcKey, itemKey)}, apply it via
   * {@link #applyFriendshipDelta}, and append a row to {@code npc_gift_log} reflecting the
   * <em>actually applied</em> friendship change (not the raw affinity table value) so weekly cap
   * audits and PassiveProfileService scans see the correct net effect.
   */
  @Transactional
  public int giveGift(long userId, String npcKey, String itemKey, int currentDay) {
    int rawDelta =
        GIFT_AFFINITY
            .getOrDefault(npcKey, Map.of())
            .getOrDefault(itemKey, NEUTRAL_GIFT_DELTA);
    int oldFriendship =
        repository
            .findByUserAndNpc(userId, npcKey)
            .map(NpcRelationship::friendship)
            .orElse(0);

    int newFriendship = applyFriendshipDelta(userId, npcKey, rawDelta, currentDay);
    int actualDelta = newFriendship - oldFriendship;
    repository.recordGift(userId, npcKey, itemKey, actualDelta);
    return newFriendship;
  }

  /**
   * Clamp the requested delta against the weekly ±{@value #WEEKLY_DELTA_CAP} window. Returns the
   * largest signed value whose sum with {@code historicalSum} stays within the cap.
   */
  private static int clampWeeklyDelta(int historicalSum, int requestedDelta) {
    int hypothetical = historicalSum + requestedDelta;
    if (hypothetical > WEEKLY_DELTA_CAP) {
      return Math.max(0, WEEKLY_DELTA_CAP - historicalSum);
    }
    if (hypothetical < -WEEKLY_DELTA_CAP) {
      return Math.min(0, -WEEKLY_DELTA_CAP - historicalSum);
    }
    return requestedDelta;
  }

  private static int clampFriendship(int value) {
    return Math.max(FRIENDSHIP_MIN, Math.min(FRIENDSHIP_MAX, value));
  }
}
