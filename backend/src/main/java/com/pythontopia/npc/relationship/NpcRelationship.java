package com.pythontopia.npc.relationship;

import java.time.OffsetDateTime;

/**
 * Per-learner × per-NPC friendship cache row backed by the {@code npc_relationship} table.
 *
 * <p>State predicates follow {@code docs/gameplay/r12-deepen/06-gift-table.md §1.1 数值范围与状态分段}:
 * stranger 0–20 / acquaintance 21–40 / friendly 41–60 / trusting 61–80 / close 81–100. The five
 * methods exposed below are pure functions over {@link #friendship} and are intended for HUD tier
 * mapping, quest gating, and soulmate gift eligibility checks elsewhere in the backend.
 */
public record NpcRelationship(
    Long id,
    Long userId,
    String npcKey,
    int friendship,
    Integer lastGiftDay,
    int weeklyDeltaSum,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  public boolean isStranger() {
    return friendship < 21;
  }

  public boolean isAcquaintance() {
    return friendship >= 21 && friendship <= 40;
  }

  public boolean isFriendly() {
    return friendship >= 41 && friendship <= 60;
  }

  public boolean isTrusting() {
    return friendship >= 61 && friendship <= 80;
  }

  public boolean isClose() {
    return friendship >= 81;
  }
}
