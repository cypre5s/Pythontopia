package com.pythontopia.world.boss;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.world.boss.dto.BossInternalProblem;
import com.pythontopia.world.boss.dto.BossPrefetchStatusDto;
import com.pythontopia.world.boss.dto.BossProblemRequest.ProfileSignalsPayload;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import com.pythontopia.common.error.BusinessException;

/**
 * Endless/Boss 题目预生成 + 「退出复用」 + 判题持久化兜底。
 *
 * <p>三件事（均落 {@code pregenerated_problem} 表，行唯一键用自增 id，problem_id 仅作检索列——AI 题
 * problemId 确定式会撞号，故不可当唯一键）：
 *
 * <ul>
 *   <li>{@link #beginPrefetch}：先同步落 PENDING，令 202 响应携带可查询 job id；FAILED 再请求会原行重试。
 *   <li>{@link #generatePrefetch}：{@code @Async} 生成后原子置 READY，失败置 FAILED 并保留原因。
 *   <li>{@link #claimReady}：取题时命中同维度最近一条 READY 行，原子标 CONSUMED 后返回完整
 *       {@link BossInternalProblem}（含 hidden testCases），省去现场等 LLM。维度严格包含 difficulty，
 *       防止低难度题被重新包装成高难度题。
 *   <li>{@link #persistConsumed} / {@link #findByProblemId}：把「现场出题并已展示给学生」的题落库 (CONSUMED)，
 *       并在内存缓存过期 / 进程重启后凭 user + problemId 取回 testCases 继续判题。
 * </ul>
 */
@Service
public class EndlessPregenService {

  private static final Logger log = LoggerFactory.getLogger(EndlessPregenService.class);
  private static final int MAX_OUTSTANDING_PER_USER = 4;
  private static final int PREFETCH_LEASE_MINUTES = 10;

  private final BossGraphClient graphClient;
  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public EndlessPregenService(
      BossGraphClient graphClient, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.graphClient = graphClient;
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  /** 同步创建或复用一个可追踪预加载任务；FAILED 任务在相同维度再次调用时进入下一次尝试。 */
  public PrefetchStart beginPrefetch(
      Long userId,
      int chapter,
      String kcCode,
      Integer difficulty,
      String kind,
      String arenaLevel) {
    if (userId == null) {
      return new PrefetchStart(
          new BossPrefetchStatusDto(
              null, "FAILED", 0, "登录状态已失效", false, OffsetDateTime.now().toString()),
          false);
    }
    try {
      recoverStalePending(userId);
      Optional<BossPrefetchStatusDto> active =
          findLatestStatus(userId, chapter, kcCode, difficulty, kind, arenaLevel, true);
      if (active.isPresent()) {
        return new PrefetchStart(active.get(), false);
      }

      Integer outstanding =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM pregenerated_problem"
                  + " WHERE user_id = ? AND status IN ('PENDING', 'READY')",
              Integer.class,
              userId);
      if (outstanding != null && outstanding >= MAX_OUTSTANDING_PER_USER) {
        throw new BusinessException(
            HttpStatus.TOO_MANY_REQUESTS,
            "BOSS_PREFETCH_LIMIT_REACHED",
            "题目预加载队列已满，请稍后重试");
      }

      Optional<BossPrefetchStatusDto> failed =
          findLatestStatus(userId, chapter, kcCode, difficulty, kind, arenaLevel, false);
      if (failed.isPresent() && "FAILED".equals(failed.get().status())) {
        int updated =
            jdbcTemplate.update(
                "UPDATE pregenerated_problem"
                    + " SET status = 'PENDING', payload = NULL, problem_id = NULL,"
                    + " error_message = NULL, attempt_count = attempt_count + 1, updated_at = now()"
                    + " WHERE id = ? AND status = 'FAILED'",
                failed.get().jobId());
        if (updated == 1) {
          BossPrefetchStatusDto retried =
              statusById(failed.get().jobId()).orElseThrow(
                  () -> new IllegalStateException("retried preload disappeared"));
          return new PrefetchStart(retried, true);
        }
      }

      Long id =
          jdbcTemplate.queryForObject(
              "INSERT INTO pregenerated_problem"
                  + " (user_id, chapter, kc_code, kind, arena_level, difficulty, payload, status,"
                  + " attempt_count, updated_at)"
                  + " VALUES (?, ?, ?, ?, ?, ?, NULL, 'PENDING', 1, now()) RETURNING id",
              Long.class,
              userId,
              chapter,
              kcCode,
              kind,
              arenaLevel,
              difficulty);
      if (id == null) {
        throw new IllegalStateException("preload insert returned no id");
      }
      BossPrefetchStatusDto created =
          statusById(id).orElseThrow(() -> new IllegalStateException("created preload disappeared"));
      return new PrefetchStart(created, true);
    } catch (DuplicateKeyException race) {
      BossPrefetchStatusDto active =
          findLatestStatus(userId, chapter, kcCode, difficulty, kind, arenaLevel, true)
              .orElseThrow(() -> race);
      return new PrefetchStart(active, false);
    }
  }

  private void recoverStalePending(Long userId) {
    jdbcTemplate.update(
        "UPDATE pregenerated_problem"
            + " SET status = 'FAILED', payload = NULL, problem_id = NULL,"
            + " error_message = '预加载超时，可重试', updated_at = now()"
            + " WHERE user_id = ? AND status = 'PENDING'"
            + " AND updated_at < now() - (? * INTERVAL '1 minute')",
        userId,
        PREFETCH_LEASE_MINUTES);
    jdbcTemplate.update(
        "DELETE FROM pregenerated_problem"
            + " WHERE user_id = ? AND status IN ('FAILED', 'CONSUMED')"
            + " AND updated_at < now() - INTERVAL '30 days'",
        userId);
  }

  /** 后台执行一个已经落为 PENDING 的任务；只有通过审查的 AI 题才可以进入 READY。 */
  @Async("bossPrefetchExecutor")
  public void generatePrefetch(
      long jobId,
      Long userId,
      int chapter,
      int stage,
      String kcCode,
      Integer difficulty,
      String preferredKc,
      String kind,
      String arenaLevel,
      ProfileSignalsPayload signals,
      int generationAttempt) {
    try {
      BossInternalProblem problem =
          graphClient.requestProblem(
              userId, chapter, stage, kcCode, difficulty, preferredKc, kind, signals);
      if (problem == null || problem.problemId() == null) {
        throw new IllegalStateException("graph returned an empty problem");
      }
      if (!problem.reviewed() || !problem.aiGenerated() || problem.testCases().isEmpty()) {
        throw new IllegalStateException("generated problem did not pass the reviewed AI contract");
      }
      int updated =
          jdbcTemplate.update(
              "UPDATE pregenerated_problem"
                  + " SET payload = ?::jsonb, problem_id = ?, status = 'READY',"
                  + " error_message = NULL, updated_at = now()"
                  + " WHERE id = ? AND status = 'PENDING' AND attempt_count = ?",
              serialize(problem),
              problem.problemId(),
              jobId,
              generationAttempt);
      if (updated != 1) {
        log.warn("prefetch job {} was no longer PENDING when generation completed", jobId);
        return;
      }
      log.info(
          "prefetch ready job={} user={} ch={} kc={} difficulty={} problemId={}",
          jobId, userId, chapter, kcCode, difficulty, problem.problemId());
    } catch (RuntimeException ex) {
      String reason = boundedError(ex.getMessage());
      jdbcTemplate.update(
          "UPDATE pregenerated_problem"
              + " SET status = 'FAILED', payload = NULL, problem_id = NULL, error_message = ?,"
              + " updated_at = now() WHERE id = ? AND status = 'PENDING' AND attempt_count = ?",
          reason,
          jobId,
          generationAttempt);
      log.warn(
          "prefetch failed job={} user={} ch={} kc={} difficulty={}: {}",
          jobId, userId, chapter, kcCode, difficulty, reason);
    }
  }

  public void failPrefetch(long jobId, int generationAttempt, String message) {
    jdbcTemplate.update(
        "UPDATE pregenerated_problem"
            + " SET status = 'FAILED', error_message = ?, updated_at = now()"
            + " WHERE id = ? AND status = 'PENDING' AND attempt_count = ?",
        boundedError(message),
        jobId,
        generationAttempt);
  }

  /** 查询同一完整维度的最新可观察状态；没有任务时返回 IDLE。 */
  public BossPrefetchStatusDto getPrefetchStatus(
      Long userId,
      int chapter,
      String kcCode,
      Integer difficulty,
      String kind,
      String arenaLevel) {
    if (userId == null) {
      return BossPrefetchStatusDto.idle();
    }
    return findLatestStatus(userId, chapter, kcCode, difficulty, kind, arenaLevel, false)
        .orElseGet(BossPrefetchStatusDto::idle);
  }

  /**
   * 取题复用：命中同维度（chapter+kc+kind+arenaLevel+difficulty，NULL-safe）最近一条 READY 行则原子标 CONSUMED
   * 并返回完整题（含 hidden testCases）；任何失败 / 未命中返回 empty 让调用方回退现场出题。
   */
  public Optional<BossInternalProblem> claimReady(
      Long userId,
      int chapter,
      String kcCode,
      Integer difficulty,
      String kind,
      String arenaLevel,
      String expeditionRunId) {
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
                  + " AND difficulty IS NOT DISTINCT FROM ?"
                  + " AND status = 'READY'"
                  + " ORDER BY created_at DESC, id DESC LIMIT 1",
              userId, chapter, kcCode, kind, arenaLevel, difficulty);
      if (rows.isEmpty()) {
        return Optional.empty();
      }
      Map<String, Object> row = rows.get(0);
      long id = ((Number) row.get("id")).longValue();
      BossInternalProblem problem = deserialize((String) row.get("payload"));
      if (problem == null) {
        return Optional.empty();
      }
      BossInternalProblem issued = problem.issueAs("issue-" + UUID.randomUUID(), kcCode);
      // 原子认领：仅当仍为 READY 才置 CONSUMED，并发下只有一个请求拿到这一行。
      int updated =
          jdbcTemplate.update(
              "UPDATE pregenerated_problem SET status = 'CONSUMED', consumed_at = now()"
                  + ", problem_id = ?, payload = ?::jsonb, expedition_run_id = ?::uuid"
                  + " WHERE id = ? AND status = 'READY'",
              issued.problemId(), serialize(issued), expeditionRunId, id);
      if (updated != 1) {
        return Optional.empty();
      }
      return Optional.of(issued);
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
      BossInternalProblem problem,
      String expeditionRunId) {
    if (userId == null || problem == null || problem.problemId() == null) {
      return;
    }
    try {
      insert(
          userId, chapter, kcCode, kind, arenaLevel, difficulty, problem, "CONSUMED", expeditionRunId);
    } catch (RuntimeException ex) {
      log.warn(
          "persistConsumed failed user={} problemId={}: {}",
          userId, problem.problemId(), ex.getMessage());
    }
  }

  /** 判题兜底：内存缓存 miss 时凭 user + problemId 取回最新一条 payload 的完整题（含 testCases）。 */
  public Optional<BossInternalProblem> findByProblemId(Long userId, String problemId) {
    if (userId == null || problemId == null) {
      return Optional.empty();
    }
    try {
      List<String> payloads =
          jdbcTemplate.query(
              "SELECT payload::text FROM pregenerated_problem"
                  + " WHERE user_id = ? AND problem_id = ?"
                  + " ORDER BY created_at DESC, id DESC LIMIT 2",
              (rs, i) -> rs.getString(1), userId, problemId);
      if (payloads.isEmpty()) {
        return Optional.empty();
      }
      if (payloads.size() > 1) {
        log.warn("ambiguous legacy problem id user={} problemId={}", userId, problemId);
        return Optional.empty();
      }
      return Optional.ofNullable(deserialize(payloads.get(0)));
    } catch (RuntimeException ex) {
      log.warn("findByProblemId failed user={} problemId={}: {}", userId, problemId, ex.getMessage());
      return Optional.empty();
    }
  }

  public Optional<String> findExpeditionRunId(Long userId, String problemId) {
    if (userId == null || problemId == null) {
      return Optional.empty();
    }
    return jdbcTemplate
        .query(
            "SELECT expedition_run_id::text FROM pregenerated_problem"
                + " WHERE user_id = ? AND problem_id = ? AND expedition_run_id IS NOT NULL"
                + " ORDER BY created_at DESC, id DESC LIMIT 1",
            (rs, rowNum) -> rs.getString(1),
            userId,
            problemId)
        .stream()
        .findFirst();
  }

  private void insert(
      Long userId,
      int chapter,
      String kcCode,
      String kind,
      String arenaLevel,
      Integer difficulty,
      BossInternalProblem problem,
      String status,
      String expeditionRunId) {
    jdbcTemplate.update(
        "INSERT INTO pregenerated_problem"
            + " (user_id, chapter, kc_code, kind, arena_level, difficulty, problem_id, payload,"
            + " status, consumed_at, expedition_run_id)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?,"
            + " CASE WHEN ? = 'CONSUMED' THEN now() ELSE NULL END, ?::uuid)",
        userId,
        chapter,
        kcCode,
        kind,
        arenaLevel,
        difficulty,
        problem.problemId(),
        serialize(problem),
        status,
        status,
        expeditionRunId);
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

  private Optional<BossPrefetchStatusDto> findLatestStatus(
      Long userId,
      int chapter,
      String kcCode,
      Integer difficulty,
      String kind,
      String arenaLevel,
      boolean activeOnly) {
    String statuses = activeOnly ? "('PENDING', 'READY')" : "('PENDING', 'READY', 'FAILED')";
    return jdbcTemplate
        .query(
            "SELECT id, status, attempt_count, error_message, updated_at"
                + " FROM pregenerated_problem WHERE user_id = ? AND chapter = ?"
                + " AND kc_code IS NOT DISTINCT FROM ?"
                + " AND kind IS NOT DISTINCT FROM ?"
                + " AND arena_level IS NOT DISTINCT FROM ?"
                + " AND difficulty IS NOT DISTINCT FROM ?"
                + " AND status IN " + statuses
                + " ORDER BY updated_at DESC, id DESC LIMIT 1",
            (rs, rowNum) -> mapStatus(rs),
            userId,
            chapter,
            kcCode,
            kind,
            arenaLevel,
            difficulty)
        .stream()
        .findFirst();
  }

  private Optional<BossPrefetchStatusDto> statusById(long id) {
    return jdbcTemplate
        .query(
            "SELECT id, status, attempt_count, error_message, updated_at"
                + " FROM pregenerated_problem WHERE id = ?",
            (rs, rowNum) -> mapStatus(rs),
            id)
        .stream()
        .findFirst();
  }

  private BossPrefetchStatusDto mapStatus(java.sql.ResultSet rs) throws java.sql.SQLException {
    String status = rs.getString("status");
    OffsetDateTime updatedAt = rs.getObject("updated_at", OffsetDateTime.class);
    return new BossPrefetchStatusDto(
        rs.getLong("id"),
        status,
        rs.getInt("attempt_count"),
        rs.getString("error_message"),
        "FAILED".equals(status) || "IDLE".equals(status),
        updatedAt == null ? null : updatedAt.toString());
  }

  private String boundedError(String message) {
    String safe = message == null || message.isBlank() ? "预加载生成失败" : message;
    return safe.length() <= 500 ? safe : safe.substring(0, 500);
  }

  /** Internal scheduling result: the public status plus whether this call owns async generation. */
  public record PrefetchStart(BossPrefetchStatusDto status, boolean shouldGenerate) {}
}
