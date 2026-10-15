package com.pythontopia.world.boss;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.config.BossProblemHttpClientProperties;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.world.boss.dto.BossInternalProblem;
import com.pythontopia.world.boss.dto.BossPrefetchStatusDto;
import com.pythontopia.world.boss.dto.BossProblemRequest.ProfileSignalsPayload;
import com.pythontopia.world.boss.review.ApprovedBossProblem;
import com.pythontopia.world.boss.review.BossProblemReviewRepository;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
  private static final long PREFETCH_RECOVERY_MARGIN_MS = Duration.ofMinutes(1).toMillis();

  private final BossGraphClient graphClient;
  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;
  private final ExpeditionRunService expeditionRunService;
  private final BossProblemReviewRepository reviewRepository;
  private final long prefetchLeaseMs;

  @Autowired
  public EndlessPregenService(
      BossGraphClient graphClient,
      JdbcTemplate jdbcTemplate,
      ObjectMapper objectMapper,
      ExpeditionRunService expeditionRunService,
      BossProblemReviewRepository reviewRepository,
      BossProblemHttpClientProperties bossProblemHttpClientProperties) {
    this.graphClient = graphClient;
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
    this.expeditionRunService = expeditionRunService;
    this.reviewRepository = reviewRepository;
    this.prefetchLeaseMs =
        bossProblemHttpClientProperties.readTimeoutMs() + PREFETCH_RECOVERY_MARGIN_MS;
  }

  EndlessPregenService(
      BossGraphClient graphClient,
      JdbcTemplate jdbcTemplate,
      ObjectMapper objectMapper,
      ExpeditionRunService expeditionRunService,
      BossProblemReviewRepository reviewRepository) {
    this(
        graphClient,
        jdbcTemplate,
        objectMapper,
        expeditionRunService,
        reviewRepository,
        new BossProblemHttpClientProperties(null));
  }

  EndlessPregenService(
      BossGraphClient graphClient,
      JdbcTemplate jdbcTemplate,
      ObjectMapper objectMapper,
      ExpeditionRunService expeditionRunService) {
    this(graphClient, jdbcTemplate, objectMapper, expeditionRunService, null);
  }

  /** Unit-test compatibility constructor for non-expedition prefetch flows. */
  EndlessPregenService(
      BossGraphClient graphClient, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this(graphClient, jdbcTemplate, objectMapper, null, null);
  }

  // shortcut: recovery assumes one worker, add ownership leases before enabling multiple replicas.
  @PostConstruct
  public void recoverAbandonedPendingAtStartup() {
    Boolean tableReady =
        jdbcTemplate.queryForObject(
            "SELECT to_regclass('public.pregenerated_problem') IS NOT NULL", Boolean.class);
    if (!Boolean.TRUE.equals(tableReady)) {
      return;
    }
    int recovered =
        jdbcTemplate.update(
            "UPDATE pregenerated_problem"
                + " SET status = 'FAILED', payload = NULL, problem_id = NULL,"
                + " error_message = '服务重启后预加载中断，可重试', updated_at = now()"
                + " WHERE status = 'PENDING'");
    if (recovered > 0) {
      log.warn("recovered {} abandoned prefetch jobs after startup", recovered);
    }
  }

  /** 同步创建或复用一个可追踪预加载任务；FAILED 任务在相同维度再次调用时进入下一次尝试。 */
  @Transactional
  public PrefetchStart beginPrefetch(
      Long userId,
      int chapter,
      int stage,
      String kcCode,
      Integer difficulty,
      String kind,
      String arenaLevel,
      String expeditionRunId) {
    return beginPrefetch(
        userId,
        chapter,
        stage,
        kcCode,
        difficulty,
        kind,
        arenaLevel,
        expeditionRunId,
        null);
  }

  /** Create a preload row bound to the validated reachable node. */
  @Transactional
  public PrefetchStart beginPrefetch(
      Long userId,
      int chapter,
      int stage,
      String kcCode,
      Integer difficulty,
      String kind,
      String arenaLevel,
      String expeditionRunId,
      String expeditionNodeId) {
    if (userId == null) {
      return new PrefetchStart(
          new BossPrefetchStatusDto(
              null, "FAILED", 0, "登录状态已失效", false, OffsetDateTime.now().toString()),
          false);
    }
    validateStage(stage);
    lockUserPrefetchQuota(userId);
    lockActiveRun(userId, expeditionRunId);
    validateExpeditionNode(userId, expeditionRunId, expeditionNodeId, chapter, kcCode, true);
    recoverStalePending(userId);
    Optional<BossPrefetchStatusDto> active =
        findLatestStatus(userId, chapter, stage, kcCode, difficulty, kind, arenaLevel,
            expeditionRunId, expeditionNodeId, true);
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
        findLatestStatus(userId, chapter, stage, kcCode, difficulty, kind, arenaLevel,
            expeditionRunId, expeditionNodeId, false);
    if (failed.isPresent() && "FAILED".equals(failed.get().status())) {
      int updated =
          jdbcTemplate.update(
              "UPDATE pregenerated_problem"
                  + " SET status = 'PENDING', stage = ?, expedition_run_id = ?::uuid,"
                  + " expedition_node_id = ?,"
                  + " payload = NULL, problem_id = NULL,"
                  + " error_message = NULL, attempt_count = attempt_count + 1, updated_at = now()"
                  + " WHERE id = ? AND status = 'FAILED'",
              stage,
              expeditionRunId,
              expeditionNodeId,
              failed.get().jobId());
      if (updated == 1) {
        BossPrefetchStatusDto retried =
            statusById(failed.get().jobId()).orElseThrow(
                () -> new IllegalStateException("retried preload disappeared"));
        return new PrefetchStart(retried, true);
      }
    }

    List<Long> insertedIds =
        jdbcTemplate.query(
            "INSERT INTO pregenerated_problem"
                + " (user_id, chapter, stage, kc_code, kind, arena_level, difficulty, payload,"
                + " status, attempt_count, expedition_run_id, expedition_node_id, updated_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, NULL, 'PENDING', 1, ?::uuid, ?, now())"
                + " ON CONFLICT DO NOTHING RETURNING id",
            (rs, rowNum) -> rs.getLong("id"),
            userId,
            chapter,
            stage,
            kcCode,
            kind,
            arenaLevel,
            difficulty,
            expeditionRunId,
            expeditionNodeId);
    if (!insertedIds.isEmpty()) {
      BossPrefetchStatusDto created =
          statusById(insertedIds.getFirst())
              .orElseThrow(() -> new IllegalStateException("created preload disappeared"));
      return new PrefetchStart(created, true);
    }
    // A concurrent request may have inserted the same active dimensions. ON CONFLICT keeps
    // PostgreSQL's transaction usable so this lookup remains valid inside @Transactional.
    BossPrefetchStatusDto activeAfterRace =
        findLatestStatus(userId, chapter, stage, kcCode, difficulty, kind, arenaLevel,
                expeditionRunId, expeditionNodeId, true)
            .orElseThrow(() -> new IllegalStateException("conflicting preload disappeared"));
    return new PrefetchStart(activeAfterRace, false);
  }

  private void recoverStalePending(Long userId) {
    failExpiredPending(userId);
    jdbcTemplate.update(
        "DELETE FROM pregenerated_problem"
            + " WHERE user_id = ? AND status = 'READY'"
            + " AND updated_at < now() - INTERVAL '1 day'",
        userId);
    jdbcTemplate.update(
        "DELETE FROM pregenerated_problem"
            + " WHERE user_id = ? AND status IN ('FAILED', 'CONSUMED')"
            + " AND updated_at < now() - INTERVAL '30 days'",
        userId);
  }

  private void failExpiredPending(Long userId) {
    jdbcTemplate.update(
        "UPDATE pregenerated_problem"
            + " SET status = 'FAILED', payload = NULL, problem_id = NULL,"
            + " error_message = '预加载超时，可重试', updated_at = now()"
            + " WHERE user_id = ? AND status = 'PENDING'"
            + " AND updated_at < now() - (? * INTERVAL '1 millisecond')",
        userId,
        prefetchLeaseMs);
  }

  /** 后台执行一个已经落为 PENDING 的任务；只有通过审查的 AI 题才可以进入 READY。 */
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
      String expeditionRunId,
      ProfileSignalsPayload signals,
      int generationAttempt) {
    generatePrefetch(
        jobId,
        userId,
        chapter,
        stage,
        kcCode,
        difficulty,
        preferredKc,
        kind,
        arenaLevel,
        expeditionRunId,
        null,
        signals,
        generationAttempt);
  }

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
      String expeditionRunId,
      String expeditionNodeId,
      ProfileSignalsPayload signals,
      int generationAttempt) {
    try {
      if (!markPendingAttemptStarted(jobId, userId, generationAttempt)) {
        log.info("skip cancelled prefetch job={} attempt={}", jobId, generationAttempt);
        return;
      }
      Optional<ApprovedBossProblem> approved =
          reviewRepository == null
              ? Optional.empty()
              : reviewRepository.findApproved(chapter, stage, kcCode, kind, difficulty);
      boolean reusedApprovedProblem = approved.isPresent();
      BossInternalProblem problem =
          approved
              .map(ApprovedBossProblem::problem)
              .orElseGet(
                  () ->
                      graphClient.requestProblem(
                          userId, chapter, stage, kcCode, difficulty, preferredKc, kind, signals));
      if (problem == null || problem.problemId() == null) {
        throw new IllegalStateException("graph returned an empty problem");
      }
      if (problem.chapter() != chapter
          || problem.stage() != stage
          || !Objects.equals(problem.kcCode(), kcCode)
          || (!reusedApprovedProblem
              && difficulty != null
              && problem.difficulty() != difficulty)) {
        throw new IllegalStateException(
            "generated problem chapter/stage/KC/difficulty did not match the request");
      }
      if (!problem.reviewed() || !problem.aiGenerated() || problem.testCases().isEmpty()) {
        throw new IllegalStateException("generated problem did not pass the reviewed AI contract");
      }
      if (reviewRepository == null) {
        throw new IllegalStateException("Boss problem approval is unavailable");
      }
      if (!reusedApprovedProblem) {
        try {
          boolean autoApproved =
              reviewRepository.approveAutomaticallyIfNotDuplicate(
                  userId, kind, chapter, stage, kcCode, difficulty, problem);
          if (!autoApproved && !reviewRepository.isApproved(problem)) {
            throw new IllegalStateException("Boss problem failed automatic approval");
          }
        } catch (DataAccessException ex) {
          log.warn(
              "could not approve pre-generated Boss problem: {}",
              ex.getClass().getSimpleName());
          throw new IllegalStateException("Boss problem approval failed", ex);
        }
      }
      int updated =
          jdbcTemplate.update(
              "UPDATE pregenerated_problem"
                  + " SET payload = ?::jsonb, problem_id = ?, status = 'READY',"
                  + " question_fingerprint = ?,"
                  + " error_message = NULL, updated_at = now()"
                  + " WHERE id = ? AND user_id = ? AND chapter = ? AND stage = ?"
                  + " AND expedition_run_id IS NOT DISTINCT FROM ?::uuid"
                  + " AND expedition_node_id IS NOT DISTINCT FROM ?"
                  + " AND status = 'PENDING' AND attempt_count = ?",
              serialize(problem),
              problem.problemId(),
              BossService.questionFingerprint(problem),
              jobId,
              userId,
              chapter,
              stage,
              expeditionRunId,
              expeditionNodeId,
              generationAttempt);
      if (updated != 1) {
        log.warn("prefetch job {} was no longer PENDING when generation completed", jobId);
        return;
      }
      log.info(
          "prefetch ready job={} user={} ch={} kc={} difficulty={} problemId={}",
          jobId, userId, chapter, kcCode, difficulty, problem.problemId());
    } catch (RuntimeException ex) {
      String reason = "题目预加载失败，可重试";
      jdbcTemplate.update(
          "UPDATE pregenerated_problem"
              + " SET status = 'FAILED', payload = NULL, problem_id = NULL, error_message = ?,"
              + " updated_at = now() WHERE id = ? AND status = 'PENDING' AND attempt_count = ?",
          reason,
          jobId,
          generationAttempt);
      log.warn(
          "prefetch failed job={} user={} ch={} kc={} difficulty={} errorType={}",
          jobId,
          userId,
          chapter,
          kcCode,
          difficulty,
          ex.getClass().getSimpleName());
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

  private boolean markPendingAttemptStarted(long jobId, Long userId, int generationAttempt) {
    return jdbcTemplate.update(
            "UPDATE pregenerated_problem SET updated_at = now()"
                + " WHERE id = ? AND user_id = ? AND status = 'PENDING' AND attempt_count = ?",
            jobId,
            userId,
            generationAttempt)
        == 1;
  }

  /** 查询同一完整维度的最新可观察状态；没有任务时返回 IDLE。 */
  public BossPrefetchStatusDto getPrefetchStatus(
      Long userId,
      int chapter,
      Integer stage,
      String kcCode,
      Integer difficulty,
      String kind,
      String arenaLevel,
      String expeditionRunId) {
    return getPrefetchStatus(
        userId,
        chapter,
        stage,
        kcCode,
        difficulty,
        kind,
        arenaLevel,
        expeditionRunId,
        null);
  }

  /** Query one exact node-bound preload status. */
  public BossPrefetchStatusDto getPrefetchStatus(
      Long userId,
      int chapter,
      Integer stage,
      String kcCode,
      Integer difficulty,
      String kind,
      String arenaLevel,
      String expeditionRunId,
      String expeditionNodeId) {
    if (userId == null) {
      return BossPrefetchStatusDto.idle();
    }
    if (stage != null) {
      validateStage(stage);
    }
    validateExpeditionNode(userId, expeditionRunId, expeditionNodeId, chapter, kcCode, true);
    failExpiredPending(userId);
    return findLatestStatus(userId, chapter, stage, kcCode, difficulty, kind, arenaLevel,
            expeditionRunId, expeditionNodeId, false)
        .orElseGet(BossPrefetchStatusDto::idle);
  }

  /**
   * 取题复用：命中同维度（chapter+kc+kind+arenaLevel+difficulty，NULL-safe）最近一条 READY 行则原子标 CONSUMED
   * 并返回完整题（含 hidden testCases）；任何失败 / 未命中返回 empty 让调用方回退现场出题。
   */
  @Transactional
  public Optional<BossInternalProblem> claimReady(
      Long userId,
      int chapter,
      int stage,
      String kcCode,
      Integer difficulty,
      String kind,
      String arenaLevel,
      String expeditionRunId) {
    return claimReady(
        userId,
        chapter,
        stage,
        kcCode,
        difficulty,
        kind,
        arenaLevel,
        expeditionRunId,
        null);
  }

  /** Claim a READY problem only when it was prepared for this reachable/current map node. */
  @Transactional
  public Optional<BossInternalProblem> claimReady(
      Long userId,
      int chapter,
      int stage,
      String kcCode,
      Integer difficulty,
      String kind,
      String arenaLevel,
      String expeditionRunId,
      String expeditionNodeId) {
    if (userId == null) {
      return Optional.empty();
    }
    validateStage(stage);
    try {
      lockActiveRun(userId, expeditionRunId);
      Integer bossPhase =
          validateExpeditionNode(userId, expeditionRunId, expeditionNodeId, chapter, kcCode, false);
      Optional<BossInternalProblem> open =
          findOpenExpeditionProblem(userId, expeditionRunId, expeditionNodeId, bossPhase);
      if (open.isPresent()) return open;
      List<Map<String, Object>> rows =
          jdbcTemplate.queryForList(
              "SELECT p.id, p.payload::text AS payload FROM pregenerated_problem p"
                  + " WHERE p.user_id = ? AND p.chapter = ? AND p.stage = ?"
                  + " AND p.kc_code IS NOT DISTINCT FROM ?"
                  + " AND p.kind IS NOT DISTINCT FROM ?"
                  + " AND p.arena_level IS NOT DISTINCT FROM ?"
                  + " AND p.difficulty IS NOT DISTINCT FROM ?"
                  + " AND p.expedition_run_id IS NOT DISTINCT FROM ?::uuid"
                  + " AND p.expedition_node_id IS NOT DISTINCT FROM ?"
                  + " AND p.status = 'READY'"
                  + " AND (?::uuid IS NULL OR NOT EXISTS (SELECT 1 FROM pregenerated_problem prior"
                  + " WHERE prior.user_id = p.user_id AND prior.expedition_run_id = p.expedition_run_id"
                  + " AND prior.status = 'CONSUMED' AND prior.question_fingerprint = p.question_fingerprint))"
                  + " ORDER BY p.created_at DESC, p.id DESC LIMIT 1",
              userId,
              chapter,
              stage,
              kcCode,
              kind,
              arenaLevel,
              difficulty,
              expeditionRunId,
              expeditionNodeId,
              expeditionRunId);
      if (rows.isEmpty()) {
        return Optional.empty();
      }
      Map<String, Object> row = rows.get(0);
      long id = ((Number) row.get("id")).longValue();
      BossInternalProblem problem = deserialize((String) row.get("payload"));
      if (problem == null || problem.chapter() != chapter || problem.stage() != stage) {
        return Optional.empty();
      }
      BossInternalProblem issued = problem.issueAs(BossService.issueProblemId(bossPhase), kcCode);
      // 原子认领：仅当仍为 READY 才置 CONSUMED，并发下只有一个请求拿到这一行。
      int updated =
          jdbcTemplate.update(
              "UPDATE pregenerated_problem SET status = 'CONSUMED', consumed_at = now()"
                  + ", problem_id = ?, payload = ?::jsonb, expedition_run_id = ?::uuid,"
                  + " expedition_node_id = ?, question_fingerprint = ?"
                  + " WHERE id = ? AND status = 'READY'"
                  + " AND expedition_run_id IS NOT DISTINCT FROM ?::uuid"
                  + " AND expedition_node_id IS NOT DISTINCT FROM ?",
              issued.problemId(),
              serialize(issued),
              expeditionRunId,
              expeditionNodeId,
              BossService.questionFingerprint(issued),
              id,
              expeditionRunId,
              expeditionNodeId);
      if (updated != 1) {
        return Optional.empty();
      }
      return Optional.of(issued);
    } catch (BusinessException exception) {
      throw exception;
    } catch (RuntimeException ex) {
      log.warn("claimReady failed errorType={}", ex.getClass().getSimpleName());
      return Optional.empty();
    }
  }

  /** 把「现场出题并已展示给学生」的题落库 (CONSUMED)，供判题兜底取回。 */
  @Transactional
  public BossInternalProblem persistConsumed(
      Long userId,
      int chapter,
      String kcCode,
      String kind,
      String arenaLevel,
      Integer difficulty,
      BossInternalProblem problem,
      String expeditionRunId) {
    return persistConsumed(
        userId,
        chapter,
        kcCode,
        kind,
        arenaLevel,
        difficulty,
        problem,
        expeditionRunId,
        null);
  }

  /** Persist a displayed question with its authoritative expedition node binding. */
  @Transactional
  public BossInternalProblem persistConsumed(
      Long userId,
      int chapter,
      String kcCode,
      String kind,
      String arenaLevel,
      Integer difficulty,
      BossInternalProblem problem,
      String expeditionRunId,
      String expeditionNodeId) {
    if (userId == null || problem == null || problem.problemId() == null) {
      return problem;
    }
    validateStage(problem.stage());
    if (problem.chapter() != chapter) {
      throw problemContextMismatch();
    }
    if (expeditionRunId != null) {
      lockActiveRun(userId, expeditionRunId);
      Integer bossPhase =
          validateExpeditionNode(userId, expeditionRunId, expeditionNodeId, chapter, kcCode, false);
      requireIssuedBossPhase(problem.problemId(), bossPhase);
      Optional<BossInternalProblem> open =
          findOpenExpeditionProblem(userId, expeditionRunId, expeditionNodeId, bossPhase);
      if (open.isPresent()) return open.orElseThrow();
      insert(
          userId, chapter, problem.stage(), kcCode, kind, arenaLevel, difficulty, problem,
          "CONSUMED", expeditionRunId, expeditionNodeId);
      return problem;
    }
    try {
      insert(
          userId, chapter, problem.stage(), kcCode, kind, arenaLevel, difficulty, problem,
          "CONSUMED", null, null);
      return problem;
    } catch (BusinessException exception) {
      throw exception;
    } catch (RuntimeException ex) {
      log.warn("persistConsumed failed errorType={}", ex.getClass().getSimpleName());
      return problem;
    }
  }

  /** Return the newest un-settled question already bound to the active challenge node. */
  public Optional<BossInternalProblem> findOpenExpeditionProblem(
      Long userId, String expeditionRunId, String expeditionNodeId, Integer bossPhase) {
    if (userId == null || expeditionRunId == null || expeditionNodeId == null) {
      return Optional.empty();
    }
    List<String> payloads =
        jdbcTemplate.query(
            "SELECT p.payload::text FROM pregenerated_problem p"
                + " JOIN expedition_run r ON r.user_id = p.user_id AND r.run_id = p.expedition_run_id"
                + " WHERE p.user_id = ? AND p.expedition_run_id = ?::uuid"
                + " AND p.expedition_node_id = ? AND p.status = 'CONSUMED'"
                + " AND r.status = 'ACTIVE' AND r.phase = 'challenge' AND r.current_node_id = p.expedition_node_id"
                + " AND ((?::int IS NULL AND p.problem_id NOT LIKE 'issue-boss-phase-%')"
                + " OR p.problem_id LIKE ('issue-boss-phase-' || ? || '-%'))"
                + " AND NOT EXISTS (SELECT 1 FROM expedition_challenge_settlement s"
                + " WHERE s.user_id = p.user_id AND s.run_id = p.expedition_run_id"
                + " AND s.node_id = p.expedition_node_id AND s.problem_id = p.problem_id)"
                + " AND p.id = (SELECT MAX(candidate.id) FROM pregenerated_problem candidate"
                + " WHERE candidate.user_id = p.user_id AND candidate.expedition_run_id = p.expedition_run_id"
                + " AND candidate.expedition_node_id = p.expedition_node_id AND candidate.status = 'CONSUMED'"
                + " AND ((?::int IS NULL AND candidate.problem_id NOT LIKE 'issue-boss-phase-%')"
                + " OR candidate.problem_id LIKE ('issue-boss-phase-' || ? || '-%'))"
                + " AND NOT EXISTS (SELECT 1 FROM expedition_challenge_settlement settled"
                + " WHERE settled.user_id = candidate.user_id AND settled.run_id = candidate.expedition_run_id"
                + " AND settled.node_id = candidate.expedition_node_id AND settled.problem_id = candidate.problem_id))"
                + " LIMIT 1",
            (rs, rowNum) -> rs.getString(1),
            userId,
            expeditionRunId,
            expeditionNodeId,
            bossPhase,
            bossPhase,
            bossPhase,
            bossPhase);
    return payloads.stream().findFirst().map(this::deserialize);
  }

  /** The exact issued problem must still be the active node's single open question. */
  public boolean isOpenExpeditionProblem(
      Long userId, String expeditionRunId, String expeditionNodeId, String problemId, Integer bossPhase) {
    return findOpenExpeditionProblem(userId, expeditionRunId, expeditionNodeId, bossPhase)
        .map(problem -> problemId.equals(problem.problemId()))
        .orElse(false);
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
                  + " WHERE user_id = ? AND problem_id = ? AND status = 'CONSUMED'"
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
      log.warn("findByProblemId failed errorType={}", ex.getClass().getSimpleName());
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
                + " WHERE user_id = ? AND problem_id = ? AND status = 'CONSUMED'"
                + " AND expedition_run_id IS NOT NULL"
                + " ORDER BY created_at DESC, id DESC LIMIT 1",
            (rs, rowNum) -> rs.getString(1),
            userId,
            problemId)
        .stream()
        .findFirst();
  }

  public Optional<ExpeditionProblemBinding> findExpeditionBinding(Long userId, String problemId) {
    if (userId == null || problemId == null) return Optional.empty();
    return jdbcTemplate
        .query(
            "SELECT expedition_run_id::text AS run_id, expedition_node_id, chapter, stage, kc_code,"
                + " question_fingerprint FROM pregenerated_problem"
                + " WHERE user_id = ? AND problem_id = ? AND status = 'CONSUMED'"
                + " AND expedition_run_id IS NOT NULL ORDER BY created_at DESC, id DESC LIMIT 1",
            (rs, rowNum) ->
                new ExpeditionProblemBinding(
                    rs.getString("run_id"),
                    rs.getString("expedition_node_id"),
                    rs.getInt("chapter"),
                    rs.getInt("stage"),
                    rs.getString("kc_code"),
                    rs.getString("question_fingerprint"),
                    BossService.bossPhaseFromProblemId(problemId)),
            userId,
            problemId)
        .stream()
        .findFirst();
  }

  private void insert(
      Long userId,
      int chapter,
      int stage,
      String kcCode,
      String kind,
      String arenaLevel,
      Integer difficulty,
      BossInternalProblem problem,
      String status,
      String expeditionRunId,
      String expeditionNodeId) {
    jdbcTemplate.update(
        "INSERT INTO pregenerated_problem"
            + " (user_id, chapter, stage, kc_code, kind, arena_level, difficulty, problem_id, payload,"
            + " status, consumed_at, expedition_run_id, expedition_node_id, question_fingerprint)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?,"
            + " CASE WHEN ? = 'CONSUMED' THEN now() ELSE NULL END, ?::uuid, ?, ?)",
        userId,
        chapter,
        stage,
        kcCode,
        kind,
        arenaLevel,
        difficulty,
        problem.problemId(),
        serialize(problem),
        status,
        status,
        expeditionRunId,
        expeditionNodeId,
        BossService.questionFingerprint(problem));
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
      log.warn(
          "deserialize pregenerated payload failed errorType={}", e.getClass().getSimpleName());
      return null;
    }
  }

  private Optional<BossPrefetchStatusDto> findLatestStatus(
      Long userId,
      int chapter,
      Integer stage,
      String kcCode,
      Integer difficulty,
      String kind,
      String arenaLevel,
      String expeditionRunId,
      String expeditionNodeId,
      boolean activeOnly) {
    String statuses = activeOnly ? "('PENDING', 'READY')" : "('PENDING', 'READY', 'FAILED')";
    return jdbcTemplate
        .query(
            "SELECT id, status, attempt_count, error_message, updated_at"
                + " FROM pregenerated_problem WHERE user_id = ? AND chapter = ?"
                + " AND (?::int IS NULL OR stage = ?)"
                + " AND kc_code IS NOT DISTINCT FROM ?"
                + " AND kind IS NOT DISTINCT FROM ?"
                + " AND arena_level IS NOT DISTINCT FROM ?"
                + " AND difficulty IS NOT DISTINCT FROM ?"
                + " AND expedition_run_id IS NOT DISTINCT FROM ?::uuid"
                + " AND expedition_node_id IS NOT DISTINCT FROM ?"
                + " AND status IN " + statuses
                + " ORDER BY updated_at DESC, id DESC LIMIT 1",
            (rs, rowNum) -> mapStatus(rs),
            userId,
            chapter,
            stage,
            stage,
            kcCode,
            kind,
            arenaLevel,
            difficulty,
            expeditionRunId,
            expeditionNodeId)
        .stream()
        .findFirst();
  }

  private void lockActiveRun(Long userId, String runId) {
    if (runId == null) {
      return;
    }
    if (userId == null
        || jdbcTemplate
            .query(
                "SELECT 1 FROM expedition_run WHERE user_id = ? AND run_id = ?::uuid"
                    + " AND status = 'ACTIVE' FOR UPDATE",
                (rs, rowNum) -> rs.getInt(1),
                userId,
                runId)
            .isEmpty()) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "EXPEDITION_RUN_EXPIRED",
          "远征存档已变化，请刷新后重试");
    }
  }

  private Integer validateExpeditionNode(
      Long userId,
      String runId,
      String nodeId,
      int chapter,
      String kcCode,
      boolean allowPrefetch) {
    if (runId == null) {
      if (nodeId != null) {
        throw new BusinessException(
            HttpStatus.CONFLICT,
            "EXPEDITION_PROBLEM_BINDING_MISMATCH",
            "题目节点绑定需要有效的远征 run");
      }
      return null;
    }
    if (expeditionRunService == null) {
      throw new IllegalStateException("ExpeditionRunService is required for run-bound problems");
    }
    return expeditionRunService.validateProblemRequest(
        userId, runId, nodeId, chapter, allowPrefetch, kcCode);
  }

  private void requireIssuedBossPhase(String problemId, Integer expectedPhase) {
    if (!java.util.Objects.equals(BossService.bossPhaseFromProblemId(problemId), expectedPhase)) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "EXPEDITION_PROBLEM_BINDING_MISMATCH",
          "题目签发阶段与当前远征 Boss 阶段不一致");
    }
  }

  private void lockUserPrefetchQuota(Long userId) {
    jdbcTemplate.queryForObject(
        "SELECT pg_advisory_xact_lock(hashtext('pythontopia.boss.prefetch.user'), hashtext(?))",
        Object.class,
        userId.toString());
  }

  private void validateStage(int stage) {
    if (stage < 1 || stage > 4) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "INVALID_BOSS_STAGE", "题目阶段必须在 1 到 4 之间");
    }
  }

  private BusinessException problemContextMismatch() {
    return new BusinessException(
        HttpStatus.CONFLICT,
        "BOSS_PROBLEM_CONTEXT_MISMATCH",
        "题目章节或阶段与签发内容不一致");
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

  public record ExpeditionProblemBinding(
      String runId,
      String nodeId,
      int chapter,
      int stage,
      String kcCode,
      String questionFingerprint,
      Integer bossPhase) {}
}
