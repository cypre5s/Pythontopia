package com.pythontopia.world.boss;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.world.boss.dto.BossInternalProblem;
import com.pythontopia.world.boss.dto.BossProblemRequest.ProfileSignalsPayload;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Endless/Boss 题目预生成 + 「退出复用」 + 判题持久化兜底。
 *
 * <p>三件事（均落 {@code pregenerated_problem} 表，行唯一键用自增 id，problem_id 仅作检索列——AI 题
 * problemId 确定式会撞号，故不可当唯一键）：
 *
 * <ul>
 *   <li>{@link #pregenerate}：{@code @Async} 后台按同维度 + 当前难度生成「下一题」并落库 (status=READY)，
 *       幂等（同 user+维度已有 READY 则跳过，成本守恒），best-effort 失败 WARN 不抛、不影响触发它的请求线程。
 *   <li>{@link #claimReady}：取题时命中同维度最近一条 READY 行，原子标 CONSUMED 后返回完整
 *       {@link BossInternalProblem}（含 hidden testCases），省去现场等 LLM。维度匹配<b>忽略 difficulty</b>
 *       （退出后再进难度可能已变，宽松匹配最大化复用）。
 *   <li>{@link #persistConsumed} / {@link #findByProblemId}：把「现场出题并已展示给学生」的题落库 (CONSUMED)，
 *       并在内存缓存过期 / 进程重启后凭 user + problemId 取回 testCases 继续判题。
 * </ul>
 */
@Service
public class EndlessPregenService {

  private static final Logger log = LoggerFactory.getLogger(EndlessPregenService.class);

  private final BossGraphClient graphClient;
  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public EndlessPregenService(
      BossGraphClient graphClient, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.graphClient = graphClient;
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  /**
   * 后台预生成「下一题」。幂等：同 user + (chapter, kc, kind, arenaLevel) 已有 READY 行则跳过。
   * best-effort：graph 不可用 / 落库失败一律 WARN 不抛，绝不影响触发它的请求线程。
   */
  @Async
  public void pregenerate(
      Long userId,
      int chapter,
      int stage,
      String kcCode,
      Integer difficulty,
      String preferredKc,
      String kind,
      String arenaLevel,
      ProfileSignalsPayload signals) {
    if (userId == null) {
      return;
    }
    try {
      if (hasReady(userId, chapter, kcCode, kind, arenaLevel)) {
        log.debug(
            "pregenerate skip (READY exists) user={} ch={} kc={} kind={} arena={}",
            userId, chapter, kcCode, kind, arenaLevel);
        return;
      }
      BossInternalProblem problem =
          graphClient.requestProblem(
              userId, chapter, stage, kcCode, difficulty, preferredKc, kind, signals);
      if (problem == null || problem.problemId() == null) {
        log.warn("pregenerate got null problem user={} ch={} kc={}", userId, chapter, kcCode);
        return;
      }
      insert(userId, chapter, kcCode, kind, arenaLevel, difficulty, problem, "READY");
      log.info(
          "pregenerate ok user={} ch={} kc={} kind={} arena={} problemId={}",
          userId, chapter, kcCode, kind, arenaLevel, problem.problemId());
    } catch (RuntimeException ex) {
      log.warn(
          "pregenerate failed user={} ch={} kc={}: {}", userId, chapter, kcCode, ex.getMessage());
    }
  }

  /**
   * 取题复用：命中同维度（chapter+kc+kind+arenaLevel，NULL-safe）最近一条 READY 行则原子标 CONSUMED
   * 并返回完整题（含 hidden testCases）；任何失败 / 未命中返回 empty 让调用方回退现场出题。
   */
  public Optional<BossInternalProblem> claimReady(
      Long userId, int chapter, String kcCode, String kind, String arenaLevel) {
    if (userId == null) {
      return Optional.empty();
    }
    try {
      List<Map<String, Object>> rows =
          jdbcTemplate.queryForList(
              "SELECT id, payload::text AS payload FROM pregenerated_problem"
                  + " WHERE user_id = ? AND chapter = ?"
                  + " AND kc_code IS NOT DISTINCT FROM ?"
                  + " AND kind IS NOT DISTINCT FROM ?"
                  + " AND arena_level IS NOT DISTINCT FROM ?"
                  + " AND status = 'READY'"
                  + " ORDER BY created_at DESC, id DESC LIMIT 1",
              userId, chapter, kcCode, kind, arenaLevel);
      if (rows.isEmpty()) {
        return Optional.empty();
      }
      Map<String, Object> row = rows.get(0);
      long id = ((Number) row.get("id")).longValue();
      // 原子认领：仅当仍为 READY 才置 CONSUMED，并发下只有一个请求拿到这一行。
      int updated =
          jdbcTemplate.update(
              "UPDATE pregenerated_problem SET status = 'CONSUMED', consumed_at = now()"
                  + " WHERE id = ? AND status = 'READY'",
              id);
      if (updated != 1) {
        return Optional.empty();
      }
      return Optional.ofNullable(deserialize((String) row.get("payload")));
    } catch (RuntimeException ex) {
      log.warn(
          "claimReady failed user={} ch={} kc={}: {}", userId, chapter, kcCode, ex.getMessage());
      return Optional.empty();
    }
  }

  /** 把「现场出题并已展示给学生」的题落库 (CONSUMED)，供判题兜底取回；best-effort 不抛。 */
  public void persistConsumed(
      Long userId,
      int chapter,
      String kcCode,
      String kind,
      String arenaLevel,
      Integer difficulty,
      BossInternalProblem problem) {
    if (userId == null || problem == null || problem.problemId() == null) {
      return;
    }
    try {
      insert(userId, chapter, kcCode, kind, arenaLevel, difficulty, problem, "CONSUMED");
    } catch (RuntimeException ex) {
      log.warn(
          "persistConsumed failed user={} problemId={}: {}",
          userId, problem.problemId(), ex.getMessage());
    }
  }

  /** 判题兜底：内存缓存 miss 时凭 user + problemId 取回最新一条 payload 的完整题（含 testCases）。 */
  public Optional<BossInternalProblem> findByProblemId(Long userId, String problemId) {
    if (problemId == null) {
      return Optional.empty();
    }
    try {
      List<String> payloads =
          userId != null
              ? jdbcTemplate.query(
                  "SELECT payload::text FROM pregenerated_problem"
                      + " WHERE user_id = ? AND problem_id = ?"
                      + " ORDER BY created_at DESC, id DESC LIMIT 1",
                  (rs, i) -> rs.getString(1), userId, problemId)
              : jdbcTemplate.query(
                  "SELECT payload::text FROM pregenerated_problem WHERE problem_id = ?"
                      + " ORDER BY created_at DESC, id DESC LIMIT 1",
                  (rs, i) -> rs.getString(1), problemId);
      if (payloads.isEmpty()) {
        return Optional.empty();
      }
      return Optional.ofNullable(deserialize(payloads.get(0)));
    } catch (RuntimeException ex) {
      log.warn("findByProblemId failed user={} problemId={}: {}", userId, problemId, ex.getMessage());
      return Optional.empty();
    }
  }

  private boolean hasReady(Long userId, int chapter, String kcCode, String kind, String arenaLevel) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM pregenerated_problem"
                + " WHERE user_id = ? AND chapter = ?"
                + " AND kc_code IS NOT DISTINCT FROM ?"
                + " AND kind IS NOT DISTINCT FROM ?"
                + " AND arena_level IS NOT DISTINCT FROM ?"
                + " AND status = 'READY'",
            Integer.class, userId, chapter, kcCode, kind, arenaLevel);
    return count != null && count > 0;
  }

  private void insert(
      Long userId,
      int chapter,
      String kcCode,
      String kind,
      String arenaLevel,
      Integer difficulty,
      BossInternalProblem problem,
      String status) {
    jdbcTemplate.update(
        "INSERT INTO pregenerated_problem"
            + " (user_id, chapter, kc_code, kind, arena_level, difficulty, problem_id, payload,"
            + " status, consumed_at)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?,"
            + " CASE WHEN ? = 'CONSUMED' THEN now() ELSE NULL END)",
        userId,
        chapter,
        kcCode,
        kind,
        arenaLevel,
        difficulty,
        problem.problemId(),
        serialize(problem),
        status,
        status);
  }

  private String serialize(BossInternalProblem problem) {
    try {
      return objectMapper.writeValueAsString(problem);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("serialize BossInternalProblem failed", e);
    }
  }

  private BossInternalProblem deserialize(String payload) {
    if (payload == null) {
      return null;
    }
    try {
      return objectMapper.readValue(payload, BossInternalProblem.class);
    } catch (JsonProcessingException e) {
      log.warn("deserialize pregenerated payload failed: {}", e.getMessage());
      return null;
    }
  }
}
