package com.pythontopia.npc.relationship;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Persistence primitives for {@code npc_relationship} and the append-only {@code npc_gift_log}
 * audit table. Higher-level rules (weekly ±10 cap, [0,100] clamp, soulmate eligibility) live in
 * {@link NpcRelationshipService}; this class intentionally exposes raw upsert / read / log-write
 * operations so future services (QuestService, PassiveProfileService) can reuse the same
 * primitives without re-implementing JDBC mapping.
 */
@Repository
public class NpcRelationshipRepository {

  private final JdbcTemplate jdbcTemplate;

  public NpcRelationshipRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<NpcRelationship> findByUserAndNpc(long userId, String npcKey) {
    return jdbcTemplate
        .query(
            """
            SELECT id, user_id, npc_key, friendship, last_gift_day, weekly_delta_sum,
                   created_at, updated_at
            FROM npc_relationship
            WHERE user_id = ? AND npc_key = ?
            """,
            this::map,
            userId,
            npcKey)
        .stream()
        .findFirst();
  }

  public List<NpcRelationship> findAllByUser(long userId) {
    return jdbcTemplate.query(
        """
        SELECT id, user_id, npc_key, friendship, last_gift_day, weekly_delta_sum,
               created_at, updated_at
        FROM npc_relationship
        WHERE user_id = ?
        ORDER BY npc_key
        """,
        this::map,
        userId);
  }

  /**
   * Insert-or-update the friendship cache row. The unique {@code (user_id, npc_key)} key resolves
   * concurrent first-write races; {@code updated_at} is refreshed on every update.
   */
  public NpcRelationship upsert(
      long userId,
      String npcKey,
      int newFriendship,
      Integer lastGiftDay,
      int weeklyDeltaSum) {
    jdbcTemplate.update(
        """
        INSERT INTO npc_relationship (user_id, npc_key, friendship, last_gift_day,
                                      weekly_delta_sum)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT (user_id, npc_key) DO UPDATE
        SET friendship = EXCLUDED.friendship,
            last_gift_day = EXCLUDED.last_gift_day,
            weekly_delta_sum = EXCLUDED.weekly_delta_sum,
            updated_at = now()
        """,
        userId,
        npcKey,
        newFriendship,
        lastGiftDay,
        weeklyDeltaSum);
    return findByUserAndNpc(userId, npcKey).orElseThrow();
  }

  /**
   * Append-only audit insert into {@code npc_gift_log}; never updates an existing row.
   *
   * <p>Writes the categorical {@code gift_tier} column inferred from {@code delta} so this skeleton
   * stays compatible with the V102 {@code npc_gift_log} schema (which declared {@code gift_tier
   * NOT NULL CHECK} before V117 re-declared the table idempotently). The mapping mirrors the
   * canonical TIER_DELTAS table in {@code docs/gameplay/r12-deepen/06-gift-table.md §2}: 8/4/1/-2/-5
   * → love/like/neutral/dislike/hate, with weekly-cap-induced 0 deltas treated as neutral.
   */
  public void recordGift(long userId, String npcKey, String itemKey, int delta) {
    jdbcTemplate.update(
        """
        INSERT INTO npc_gift_log (user_id, npc_key, item_key, gift_tier, friendship_delta)
        VALUES (?, ?, ?, ?, ?)
        """,
        userId,
        npcKey,
        itemKey,
        inferGiftTier(delta),
        delta);
  }

  static String inferGiftTier(int delta) {
    if (delta >= 8) {
      return "love";
    }
    if (delta >= 4) {
      return "like";
    }
    if (delta >= 0) {
      return "neutral";
    }
    if (delta >= -2) {
      return "dislike";
    }
    return "hate";
  }

  /**
   * Sum the {@code friendship_delta} written into {@link #recordGift} within the last 7 real days.
   *
   * <p>{@code currentDay} is the in-game {@code WorldClock.dayIndex} reserved for the future
   * implementation that windows by game day rather than wall clock; today it is accepted so the
   * callsite contract is stable and so service-layer tests can pre-seed the day index without
   * later signature churn.
   */
  public int sumWeeklyDelta(long userId, String npcKey, int currentDay) {
    Integer sum =
        jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(SUM(friendship_delta), 0)
            FROM npc_gift_log
            WHERE user_id = ?
              AND npc_key = ?
              AND given_at >= now() - INTERVAL '7 days'
            """,
            Integer.class,
            userId,
            npcKey);
    return sum == null ? 0 : sum;
  }

  private NpcRelationship map(ResultSet rs, int rowNum) throws SQLException {
    int lastGiftDayValue = rs.getInt("last_gift_day");
    Integer lastGiftDay = rs.wasNull() ? null : lastGiftDayValue;
    return new NpcRelationship(
        rs.getLong("id"),
        rs.getLong("user_id"),
        rs.getString("npc_key"),
        rs.getInt("friendship"),
        lastGiftDay,
        rs.getInt("weekly_delta_sum"),
        offset(rs, "created_at"),
        offset(rs, "updated_at"));
  }

  private OffsetDateTime offset(ResultSet rs, String column) throws SQLException {
    java.sql.Timestamp ts = rs.getTimestamp(column);
    return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
  }
}
