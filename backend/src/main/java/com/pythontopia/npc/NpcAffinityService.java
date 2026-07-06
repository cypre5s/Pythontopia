package com.pythontopia.npc;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** NPC 与学生的关系强度：纯规则累积，tier = floor(score) clamp 到 0..4。 */
@Service
public class NpcAffinityService {

  private final JdbcTemplate jdbc;

  public NpcAffinityService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void bump(long userId, String npcKey, double delta) {
    double safeDelta = Math.max(0.0, delta);
    jdbc.update(
        "INSERT INTO npc_affinity (user_id, npc_key, score, tier) "
            + "VALUES (?, ?, ?, ?) "
            + "ON CONFLICT (user_id, npc_key) DO UPDATE SET "
            + "score = npc_affinity.score + EXCLUDED.score, "
            + "tier = LEAST(4, FLOOR(npc_affinity.score + EXCLUDED.score)::smallint), "
            + "updated_at = now()",
        userId,
        npcKey,
        safeDelta,
        tierForScore(safeDelta));
  }

  public int tier(long userId, String npcKey) {
    Integer tier =
        jdbc.query(
                "SELECT tier FROM npc_affinity WHERE user_id = ? AND npc_key = ?",
                (rs, rowNum) -> rs.getInt("tier"),
                userId,
                npcKey)
            .stream()
            .findFirst()
            .orElse(0);
    return tier == null ? 0 : tier;
  }

  static int tierForScore(double score) {
    return Math.max(0, Math.min(4, (int) Math.floor(score)));
  }
}
