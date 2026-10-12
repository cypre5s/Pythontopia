package com.pythontopia.world.boss;

import com.pythontopia.coursepack.domain.CourseKc;
import com.pythontopia.coursepack.repository.CoursePackRepository;
import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.learner.mastery.MasteryRepository;
import com.pythontopia.learner.mastery.MasterySnapshot;
import com.pythontopia.learner.profile.dto.LearnerProfileSignals;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import com.pythontopia.mainline.ChapterCatalog;
import com.pythontopia.world.boss.dto.BossAttemptRequest;
import com.pythontopia.world.boss.dto.BossAttemptResultDto;
import com.pythontopia.world.boss.dto.BossEvaluateResultDto;
import com.pythontopia.world.boss.dto.BossInternalProblem;
import com.pythontopia.world.boss.dto.BossPrefetchStatusDto;
import com.pythontopia.world.boss.dto.BossProblemDto;
import com.pythontopia.world.boss.dto.BossProblemRequest;
import com.pythontopia.world.boss.dto.BossProblemRequest.ProfileSignalsPayload;
import com.pythontopia.world.boss.dto.BossTestCasePayload;
import com.pythontopia.world.boss.dto.FrontierSelectRequest;
import com.pythontopia.world.boss.dto.FrontierSelectResultDto;
import com.pythontopia.world.boss.review.ApprovedBossProblem;
import com.pythontopia.world.boss.review.BossProblemReviewRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * Boss 副本服务。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>把 chapter + stage 翻译成默认 KC 范围（与前端 {@code MAIN_QUEST_BOSSES} 表一致）；
 *   <li>调 {@link BossGraphClient} 拉 AI 出题，失败时退化到 golden 兜底题；
 *   <li>用进程内 cache 持有完整 {@link BossInternalProblem}（含 hidden testCases），
 *       {@link #evaluateAttempt} 时取出转发给 graph evaluate；
 *   <li>graph 评测失败时返回结构化兜底，提示前端用 Pyodide 校验。
 * </ul>
 *
 * <p>缓存 TTL 30 分钟（覆盖单局 4 阶段时长 + 重连缓冲），超时后未提交的题目失效。
 */
@Service
public class BossService {

  private static final Logger LOG = LoggerFactory.getLogger(BossService.class);
  private static final Duration CACHE_TTL = Duration.ofMinutes(30);
  private static final int EVAL_TIMEOUT_SEC = 5;
  private static final long DEFAULT_PACK_ID = 43L;
  private static final String ISSUE_ID_PREFIX = "issue-";

  private final BossGraphClient graphClient;
  private final MasteryRepository masteryRepository;
  private final CoursePackRepository coursePackRepository;
  private final PassiveProfileService passiveProfileService;
  private final EndlessPregenService pregenService;
  private final BossProblemReviewRepository reviewRepository;
  private final ExpeditionRunService expeditionRunService;
  private final ExpeditionScoreService expeditionScoreService;
  private final ApplicationEventPublisher eventPublisher;
  private final ConcurrentMap<ProblemCacheKey, CachedProblem> cache = new ConcurrentHashMap<>();

  public BossService(
      BossGraphClient graphClient,
      MasteryRepository masteryRepository,
      CoursePackRepository coursePackRepository,
      PassiveProfileService passiveProfileService,
      EndlessPregenService pregenService,
      BossProblemReviewRepository reviewRepository,
      ExpeditionRunService expeditionRunService,
      ExpeditionScoreService expeditionScoreService,
      ApplicationEventPublisher eventPublisher) {
    this.graphClient = graphClient;
    this.masteryRepository = masteryRepository;
    this.coursePackRepository = coursePackRepository;
    this.passiveProfileService = passiveProfileService;
    this.pregenService = pregenService;
    this.reviewRepository = reviewRepository;
    this.expeditionRunService = expeditionRunService;
    this.expeditionScoreService = expeditionScoreService;
    this.eventPublisher = eventPublisher;
  }

  /**
   * 无尽远征知识前沿选择（下沉自前端）：从权威掌握度 + 画像 + 近期 KC，调 graph 选择器返回候选。
   *
   * <p>graph 不可达或返回空结果时显式 503；禁止用前端默认 KC 冒充画像自适应结果。
   */
  public FrontierSelectResultDto selectFrontier(Long userId, FrontierSelectRequest request) {
    Map<String, Double> mastery = new HashMap<>();
    Map<String, Double> confidence = new HashMap<>();
    // 间隔重复只看正式练习/调试证据；last_event_at 也会被 DECAY 更新，不能充当最近练习时间。
    Map<String, String> lastPracticed = new HashMap<>();
    if (userId != null) {
      masteryRepository
          .findLastPracticedAtByUser(userId)
          .forEach(
              (kcCode, practicedAt) ->
                  lastPracticed.put(kcCode, practicedAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)));
      for (MasterySnapshot snapshot : masteryRepository.findAll(userId)) {
        if (snapshot.kcCode() == null) {
          continue;
        }
        if (snapshot.masteryScore() != null) {
          mastery.put(snapshot.kcCode(), snapshot.masteryScore().doubleValue());
        }
        if (snapshot.confidence() != null) {
          confidence.put(snapshot.kcCode(), snapshot.confidence().doubleValue());
        }
      }
    }
    try {
      FrontierSelectResultDto result =
          graphClient.selectFrontier(
              mastery,
              confidence,
              authoritativeSignals(userId, request.profileSignals()),
              request.recentKcs(),
              request.focusChapter(),
              request.exclude(),
              request.count(),
              lastPracticed);
      if (result == null || result.picks().isEmpty()) {
        throw new RestClientException("frontier selector returned no evidence-backed picks");
      }
      return result;
    } catch (RestClientException ex) {
      LOG.warn("select-frontier graph unavailable: {}", ex.getMessage());
      throw new com.pythontopia.common.error.BusinessException(
          org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
          "EXPEDITION_ADAPTATION_UNAVAILABLE",
          "学习证据暂时不可用，请稍后重试远征");
    }
  }

  public BossProblemDto fetchProblem(int chapter, BossProblemRequest request, Long userId) {
    // preferredKc 优先级高于旧 kcCode（前端 ArenaPage 走 preferredKc）。
    String effectiveKc = request.preferredKc() != null ? request.preferredKc() : request.kcCode();
    String kc = resolveKc(chapter, effectiveKc);
    String expeditionRunId =
        validateExpeditionRun(userId, request.expeditionRunId(), chapter);
    // 难度优先取前端显式请求；否则据该章 KC 的真实掌握度 + 置信度推断（学情驱动），而非仅按 stage。
    int difficulty =
        computeTargetDifficulty(userId, kc, request.stage(), request.difficulty());

    // 1) 复用「已预生成未做」(READY) 的同维度题：命中即原子标 CONSUMED，省去现场等 LLM。
    //    这条同时实现「学生中途退出，下次登入复用已生成未做的题」——上次会话预取留下的 READY 行。
    Optional<BossInternalProblem> reused =
        pregenService.claimReady(
            userId, chapter, request.stage(), kc, difficulty, request.preferredKind(), request.arenaLevel(),
            expeditionRunId);
    if (reused.isPresent()) {
      BossInternalProblem p = reused.get();
      cache.put(new ProblemCacheKey(userId, p.problemId()), new CachedProblem(p, Instant.now()));
      evictExpired();
      LOG.info("boss reuse pregenerated problemId={} user={} ch={}", p.problemId(), userId, chapter);
      // 展示时套用「本次请求」的 stage + 本次推断难度：预取时的 stage/难度不串到当前上下文
      // （也避免共享端点下复用题把异常 stage 显示给主线 boss）。
      requireProblemContext(p, chapter, request.stage());
      return p.toPublic();
    }

    // 2) 没有该学生的 READY 个性化题时，再查教师已通过的全局题库：按
    //    chapter/stage/KC/kind 只读选择最接近目标难度的版本。题库 payload 含 hidden tests，
    //    只在后端回灌缓存并为当前学生落判题兜底，前端仅拿公开投影。
    Optional<ApprovedBossProblem> approved =
        reviewRepository.findApproved(
            chapter, request.stage(), kc, request.preferredKind(), difficulty);
    if (approved.isPresent()) {
      BossInternalProblem source = approved.get().problem();
      requireProblemContext(source, chapter, request.stage());
      BossInternalProblem problem = source.issueAs(ISSUE_ID_PREFIX + UUID.randomUUID(), kc);
      cache.put(new ProblemCacheKey(userId, problem.problemId()), new CachedProblem(problem, Instant.now()));
      pregenService.persistConsumed(
          userId,
          chapter,
          kc,
          request.preferredKind(),
          request.arenaLevel(),
          difficulty,
          problem,
          expeditionRunId);
      evictExpired();
      LOG.info(
          "boss selected approved review={} version={} problemId={} user={} ch={}",
          approved.get().reviewId(),
          approved.get().version(),
          problem.problemId(),
          userId,
          chapter);
      return problem.toPublic();
    }

    // 3) 个性化预生成与 approved bank 均未命中 → 现场走 graph 出题（失败回退 golden）。
    try {
      BossInternalProblem fromGraph =
          graphClient.requestProblem(
              userId,
              chapter,
              request.stage(),
              kc,
              difficulty,
              request.preferredKc(),
              request.preferredKind(),
              authoritativeSignals(userId, request.profileSignals()));
      if (fromGraph != null && fromGraph.problemId() != null) {
        requireProblemContext(fromGraph, chapter, request.stage());
        BossInternalProblem issued = fromGraph.issueAs(ISSUE_ID_PREFIX + UUID.randomUUID(), kc);
        cache.put(new ProblemCacheKey(userId, issued.problemId()), new CachedProblem(issued, Instant.now()));
        // 现场题也落库 (CONSUMED)，供内存缓存过期 / 进程重启后判题兜底取回 testCases（best-effort）。
        pregenService.persistConsumed(
            userId, chapter, kc, request.preferredKind(), request.arenaLevel(), difficulty,
            issued, expeditionRunId);
        evictExpired();
        return issued.toPublic();
      }
    } catch (RestClientException ex) {
      LOG.warn(
          "graph boss problem failed, fallback to golden ({}): {}",
          ex.getClass().getSimpleName(),
          ex.getMessage());
    }
    return buildGoldenProblem(chapter, request.stage(), kc, difficulty);
  }

  /**
   * 触发后台预生成「下一题」：按同维度 + 当前难度异步生成并落 {@code pregenerated_problem} (READY)。
   * 立即返回带 job id 的 PENDING/READY，真正生成在
   * {@link EndlessPregenService#generatePrefetch} 的 {@code @Async} 线程进行；失败落 FAILED，可重试。
   */
  public BossPrefetchStatusDto prefetch(int chapter, BossProblemRequest request, Long userId) {
    String effectiveKc = request.preferredKc() != null ? request.preferredKc() : request.kcCode();
    String kc = resolveKc(chapter, effectiveKc);
    String expeditionRunId =
        validateExpeditionRun(userId, request.expeditionRunId(), chapter);
    int difficulty =
        computeTargetDifficulty(userId, kc, request.stage(), request.difficulty());
    EndlessPregenService.PrefetchStart start =
        pregenService.beginPrefetch(
            userId, chapter, request.stage(), kc, difficulty, request.preferredKind(),
            request.arenaLevel(), expeditionRunId);
    if (start.shouldGenerate() && start.status().jobId() != null) {
      try {
        pregenService.generatePrefetch(
            start.status().jobId(),
            userId,
            chapter,
            request.stage(),
            kc,
            difficulty,
            request.preferredKc(),
            request.preferredKind(),
            request.arenaLevel(),
            expeditionRunId,
            authoritativeSignals(userId, request.profileSignals()),
            start.status().attempts());
      } catch (TaskRejectedException rejected) {
        pregenService.failPrefetch(
            start.status().jobId(), start.status().attempts(), "预加载队列已满，请稍后重试");
        throw new com.pythontopia.common.error.BusinessException(
            org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
            "BOSS_PREFETCH_QUEUE_FULL",
            "题目预加载队列已满，请稍后重试");
      }
    }
    return start.status();
  }

  /** 查询预加载状态；维度与取题复用完全同构，尤其包含 difficulty。 */
  public BossPrefetchStatusDto prefetchStatus(
      int chapter,
      Integer stage,
      String preferredKc,
      Integer requestedDifficulty,
      String preferredKind,
      String arenaLevel,
      String expeditionRunId,
      Long userId) {
    String kc = resolveKc(chapter, preferredKc);
    int progression = requestedDifficulty == null ? 1 : requestedDifficulty;
    int difficulty =
        computeTargetDifficulty(userId, kc, progression, requestedDifficulty);
    String activeRunId = validateExpeditionRun(userId, expeditionRunId, chapter);
    return pregenService.getPrefetchStatus(
        userId, chapter, stage, kc, difficulty, preferredKind, arenaLevel, activeRunId);
  }

  /**
   * 出题画像以服务端 unified dimensions 为权威，避免客户端遗漏/篡改导致信号漂移；只有匿名调用
   * 才沿用请求内画像。访谈 metadata 是自评快照，不替代下方正式掌握度与练习历史。
   */
  private ProfileSignalsPayload authoritativeSignals(
      Long userId, ProfileSignalsPayload requestSignals) {
    if (userId == null) {
      return requestSignals;
    }
    LearnerProfileSignals profile = passiveProfileService.getPersonalizationSignals(userId);
    return new ProfileSignalsPayload(
        profile.frequentKcCodes(),
        profile.pythonSelfRatedLevel(),
        profile.successRate(),
        profile.mediaPriority(),
        profile.resilience(),
        requestSignals == null ? null : requestSignals.questPreferredKind());
  }

  public BossAttemptResultDto evaluateAttempt(BossAttemptRequest request, Long userId) {
    Optional<String> expeditionRunId =
        pregenService.findExpeditionRunId(userId, request.problemId());
    if (expeditionRunId.isPresent()
        && !expeditionRunService.isActiveRun(userId, expeditionRunId.get())) {
      throw expiredExpeditionRun();
    }
    // 判题取题：先内存缓存（TTL 内有效），过期 / 缺失再回退 pregenerated_problem 凭 problemId 取回，
    // 修复「内存缓存过期 / 进程重启 → 判题失效」。
    BossInternalProblem problem = resolveProblemForEval(request.problemId(), userId);
    if (problem == null) {
      LOG.info(
          "boss attempt problemId={} not in cache nor persisted, returning Pyodide-only fallback",
          request.problemId());
      return new BossAttemptResultDto(
          request.problemId(),
          false,
          0,
          3,
          null,
          0,
          "题目缓存过期且无持久化记录；请用前端 Pyodide 校验后再次提交，或重新生成题目。");
    }
    requireProblemContext(problem, request.chapter(), request.stage());
    List<BossTestCasePayload> testCases = problem.testCases();
    if (testCases.isEmpty()) {
      LOG.warn("problem {} has empty testCases (graph schema drift?)", request.problemId());
      return new BossAttemptResultDto(
          request.problemId(),
          false,
          0,
          problem.totalTestCases(),
          null,
          0,
          "题目缓存数据异常（缺 testCases），请重试生成题目。");
    }
    try {
      BossEvaluateResultDto graphResult =
          graphClient.evaluateAttempt(
              request.problemId(), request.code(), testCases, EVAL_TIMEOUT_SEC);
      if (graphResult == null) {
        throw new RestClientException("graph evaluate returned null body");
      }
      int score = scoreFor(graphResult);
      String runId = expeditionRunId.orElse(null);
      if (!recordVerifiedExpeditionAttempt(userId, runId, request.problemId(), graphResult, score)) {
        if (!expeditionRunService.isActiveRun(userId, runId)) {
          throw expiredExpeditionRun();
        }
        throw new com.pythontopia.common.error.BusinessException(
            org.springframework.http.HttpStatus.CONFLICT,
            "EXPEDITION_ATTEMPT_LIMIT_REACHED",
            "本局可计分判题次数已用完");
      }
      publishPracticeOutcome(userId, problem, graphResult);
      String feedback =
          graphResult.passed()
              ? "通过 ✅ 服务端沙箱已确认。"
              : graphResult.runtimeError() != null
                  ? "代码运行错误：" + graphResult.runtimeError()
                  : "未通过 ❌ 部分用例失败，请参考下方 hint。";
      // 闭环关键：服务端沙箱真实判完后（且仅此路径）才发学习事件，与 SubmissionService 同一套事件驱动
      // KC 掌握度（测）+ 8 维画像被动修订。缓存过期 / 缺 testCases / graph 不可用等「未判定」路径不发，
      // 避免把非判题结果污染进掌握度。单次 attempt 仅发一次，天然幂等。
      return new BossAttemptResultDto(
          request.problemId(),
          graphResult.passed(),
          graphResult.passedTestCases(),
          graphResult.totalTestCases(),
          graphResult.failureFirstHint(),
          score,
          feedback);
    } catch (RestClientException ex) {
      LOG.warn(
          "graph evaluate failed ({}): {} — falling back to local Pyodide hint",
          ex.getClass().getSimpleName(),
          ex.getMessage());
      return new BossAttemptResultDto(
          request.problemId(),
          false,
          0,
          problem.totalTestCases(),
          null,
          0,
          "graph 评测服务不可用，请用前端 Pyodide 校验后再次提交。");
    }
  }

  private String validateExpeditionRun(Long userId, String runId, int chapter) {
    if (runId == null) {
      return null;
    }
    expeditionRunService.validateProblemRequest(userId, runId, chapter);
    return runId;
  }

  private boolean recordVerifiedExpeditionAttempt(
      Long userId, String runId, String problemId, BossEvaluateResultDto result, int score) {
    if (userId == null || runId == null) {
      return true;
    }
    return expeditionScoreService.recordVerifiedAttempt(
        userId, runId, problemId, result.passed(), score);
  }

  private void requireProblemContext(BossInternalProblem problem, int chapter, int stage) {
    if (problem.chapter() != chapter || problem.stage() != stage) {
      throw new com.pythontopia.common.error.BusinessException(
          org.springframework.http.HttpStatus.CONFLICT,
          "BOSS_PROBLEM_CONTEXT_MISMATCH",
          "题目章节或阶段与签发内容不一致");
    }
  }

  private com.pythontopia.common.error.BusinessException expiredExpeditionRun() {
    return new com.pythontopia.common.error.BusinessException(
        org.springframework.http.HttpStatus.CONFLICT,
        "EXPEDITION_RUN_EXPIRED",
        "远征存档已变化，请刷新后重试");
  }

  /**
   * 把一次 boss / endless 的服务端判题结果发布为 {@link LearningEvent}：通过 → {@code PracticeSucceeded}，
   * 未通过 → {@code PracticeFailed}（verdict 取 RE/WA），与 {@code SubmissionService} 同一套事件，
   * 统一驱动 KC 掌握度与 8 维画像被动修订（修复 /endless、/arena 练习此前不回流学情的断环）。
   *
   * <p>boss 题为 AI 现场生成、无 {@code course_problem} 数字主键，故事件 {@code problemId} 传 {@code null}
   * （各监听器对 null 均安全）；难度按题目 1..5 映射到 mastery 可识别的档位字符串，保留难度加权语义。
   * userId 为空（匿名/未鉴权）或题目缺 kcCode 时安全跳过，绝不抛错影响判题返回。
   */
  private void publishPracticeOutcome(
      Long userId, BossInternalProblem problem, BossEvaluateResultDto result) {
    if (userId == null) {
      return;
    }
    String kcCode = problem.kcCode();
    if (kcCode == null || kcCode.isBlank()) {
      LOG.warn("boss attempt problemId={} has no kcCode; skip LearningEvent", problem.problemId());
      return;
    }
    String difficulty = difficultyBand(problem.difficulty());
    if (result.passed()) {
      eventPublisher.publishEvent(
          new LearningEvent.PracticeSucceeded(userId, kcCode, null, difficulty));
    } else {
      String verdict =
          result.runtimeError() != null && !result.runtimeError().isBlank() ? "RE" : "WA";
      eventPublisher.publishEvent(
          new LearningEvent.PracticeFailed(userId, kcCode, null, verdict, difficulty));
    }
  }

  /**
   * Boss 难度 int(1..5) → mastery 难度系数可识别的档位字符串（驱动难度加权：易题失败重罚、难题通过厚奖，
   * 见 {@code MasteryProperties.difficultyMultiplier}）。1..2→easy，3→medium，4..5→hard。
   */
  static String difficultyBand(int difficulty) {
    if (difficulty <= 2) {
      return "easy";
    }
    if (difficulty >= 4) {
      return "hard";
    }
    return "medium";
  }

  /**
   * 取判题所需完整题（含 hidden testCases）：先查进程内缓存（TTL 内有效）；过期 / 未命中只从
   * {@code pregenerated_problem} 按 user + issued problemId 取回。审核题在发放前会先签发用户专属 ID
   * 并写入该表；全局审核库 ID 不可直接用于判题。未找到用户记录即返回 {@code null}。
   */
  private BossInternalProblem resolveProblemForEval(String problemId, Long userId) {
    ProblemCacheKey key = new ProblemCacheKey(userId, problemId);
    CachedProblem cached = cache.get(key);
    if (cached != null) {
      if (Duration.between(cached.cachedAt(), Instant.now()).compareTo(CACHE_TTL) <= 0) {
        return cached.problem();
      }
      cache.remove(key); // 过期，落 DB 再取
    }
    BossInternalProblem fromDb = pregenService.findByProblemId(userId, problemId).orElse(null);
    if (fromDb != null) {
      cache.put(key, new CachedProblem(fromDb, Instant.now())); // 回灌缓存避免重复查库
    }
    return fromDb;
  }

  /** 缓存清理：size 上限 + TTL 双保险，避免无限增长。 */
  private void evictExpired() {
    if (cache.size() < 256) {
      return;
    }
    Instant now = Instant.now();
    cache.entrySet().removeIf(e -> Duration.between(e.getValue().cachedAt(), now).compareTo(CACHE_TTL) > 0);
  }

  /** Boss 目标难度 1..5；无显式纵深时以 stage 作为递进主轴。 */
  int computeTargetDifficulty(Long userId, String anchorKc, int stage) {
    return computeTargetDifficulty(userId, anchorKc, stage, null);
  }

  /**
   * 难度由两部分合成：地图纵深/关卡请求给出递进主轴，服务端掌握度与画像给出 readiness。
   * readiness 只允许在当前层上下调整一档，既避免客户端把任意难度当权威，也保证同一学生随
   * 纵深单调递进，不会因为画像较弱而在深层突然掉回新手题。
   */
  int computeTargetDifficulty(
      Long userId, String anchorKc, int stage, Integer requestedProgression) {
    int progression = clampDifficulty(requestedProgression == null ? stage : requestedProgression);
    if (userId == null || anchorKc == null) {
      return progression;
    }
    try {
      Integer masteryDifficulty = masteryDifficulty(userId, anchorKc);
      Integer profileDifficulty =
          profileDifficulty(passiveProfileService.getPersonalizationSignals(userId));
      Integer readiness = averageDifficulty(masteryDifficulty, profileDifficulty);
      if (readiness == null) {
        return progression;
      }
      return clampDifficulty(progression + Integer.compare(readiness, progression));
    } catch (RuntimeException ex) {
      LOG.debug("boss target difficulty degraded for user {}: {}", userId, ex.getMessage());
      return progression;
    }
  }

  private Integer masteryDifficulty(Long userId, String anchorKc) {
    List<CourseKc> kcs = coursePackRepository.findKcs(DEFAULT_PACK_ID, null);
    Long chapterId = null;
    for (CourseKc kc : kcs) {
      if (anchorKc.equals(kc.kcCode())) {
        chapterId = kc.chapterId();
        break;
      }
    }
    if (chapterId == null) {
      return null;
    }
    Set<String> chapterKcCodes = new HashSet<>();
    for (CourseKc kc : kcs) {
      if (chapterId.equals(kc.chapterId()) && kc.kcCode() != null) {
        chapterKcCodes.add(kc.kcCode());
      }
    }
    double scoreSum = 0.0;
    double confSum = 0.0;
    int n = 0;
    for (MasterySnapshot s : masteryRepository.findAll(userId)) {
      if (s.masteryScore() == null || !chapterKcCodes.contains(s.kcCode())) {
        continue;
      }
      scoreSum += s.masteryScore().doubleValue();
      confSum += s.confidence() == null ? 0.5 : s.confidence().doubleValue();
      n++;
    }
    if (n == 0) {
      return null;
    }
    return bossDifficulty(scoreSum / n, confSum / n);
  }

  static Integer profileDifficulty(LearnerProfileSignals profile) {
    if (profile == null) {
      return null;
    }
    List<Integer> signals = new java.util.ArrayList<>();
    if (profile.pythonSelfRatedLevel() != null) {
      int level = profile.pythonSelfRatedLevel();
      signals.add(clampDifficulty(level == 0 ? 1 : level));
    }
    if (profile.successRate() != null) {
      double rate = Math.max(0.0, Math.min(1.0, profile.successRate()));
      signals.add(clampDifficulty(1 + (int) Math.floor(rate * 5.0)));
    }
    if (profile.avgAttemptsPerProblem() != null) {
      double attempts = profile.avgAttemptsPerProblem();
      signals.add(attempts <= 1.2 ? 5 : attempts <= 2.0 ? 4 : attempts <= 3.0 ? 3 : 2);
    }
    if (signals.isEmpty()) {
      return null;
    }
    int readiness = (int) Math.round(signals.stream().mapToInt(Integer::intValue).average().orElse(3));
    if (profile.resilience() != null) {
      double resilience = profile.resilience();
      if (resilience < 0.35) {
        readiness--;
      } else if (resilience > 0.8) {
        readiness++;
      }
    }
    return clampDifficulty(readiness);
  }

  private static Integer averageDifficulty(Integer mastery, Integer profile) {
    if (mastery == null) {
      return profile;
    }
    if (profile == null) {
      return mastery;
    }
    return clampDifficulty((int) Math.round(mastery * 0.7 + profile * 0.3));
  }

  private static int clampDifficulty(int difficulty) {
    return Math.min(5, Math.max(1, difficulty));
  }

  /**
   * 掌握度 + 置信度 → 难度 1..5 的纯函数（便于直接单测）：低掌握→易（建立信心），
   * 高掌握 + 高置信且逼近本档上沿→难（促成长）；低置信度降一档。
   */
  static int bossDifficulty(double score, double confidence) {
    int base = score < 0.2 ? 1 : score < 0.4 ? 2 : score < 0.6 ? 3 : score < 0.8 ? 4 : 5;
    if (confidence < 0.4) {
      return Math.max(1, base - 1);
    }
    if (confidence >= 0.7 && nearBandTop(score)) {
      return Math.min(5, base + 1);
    }
    return base;
  }

  /** 逼近本 0.2 宽难度档上沿（本档后 1/4 区间）。 */
  private static boolean nearBandTop(double score) {
    double fraction = score * 5.0 - Math.floor(score * 5.0);
    return fraction >= 0.75;
  }

  /**
   * 章号 1..7 → 默认锚定 KC（该章起始 KC code）。权威映射统一走 {@link ChapterCatalog}
   * （与 {@code data/course-pack-43/json/course_kc.json} 对齐：ch1=001..007、ch2=008..016、
   * ch3=017..030、ch4=031..038、ch5=039..047、ch6=048..053、ch7=054..065）。
   *
   * <p>修复旧硬编码表 ch2..7 整体「错位一章」的缺陷（如旧值 ch2→KC-006、ch3→KC-016 实际分别属于
   * 第 1、2 章），该错位会让 boss 出题与学情聚合锚到错误章节的 KC。不再臆造 KC，全部源自课程包。
   */
  String resolveKc(int chapter, String override) {
    int safeChapter = ChapterCatalog.isValidChapter(chapter) ? chapter : 1;
    if (override != null && !override.isBlank()) {
      String normalized = override.trim();
      if (ChapterCatalog.chapterOfKc(normalized).filter(kcChapter -> kcChapter == safeChapter).isEmpty()) {
        throw new com.pythontopia.common.error.BusinessException(
            org.springframework.http.HttpStatus.BAD_REQUEST,
            "INVALID_BOSS_KC",
            "KC 必须属于指定章节的 Python 语言基础课程内容");
      }
      return normalized;
    }
    return ChapterCatalog.kcCodes(safeChapter).get(0);
  }

  BossProblemDto buildGoldenProblem(int chapter, int stage, String kcCode, int difficulty) {
    String title = String.format(Locale.ROOT, "第 %d 章 Stage %d · Golden", chapter, stage);
    String statement =
        "Graph 服务未连接，使用 golden 兜底题。\n\n请实现：读入两个整数 a b，输出 a+b。";
    return new BossProblemDto(
        String.format(Locale.ROOT, "golden-c%d-s%d", chapter, stage),
        chapter,
        stage,
        Math.min(5, Math.max(1, difficulty)),
        kcCode,
        title + "\n\n" + statement,
        List.of("用 input().split() 拿到两段字符串", "map(int, ...) 转 int"),
        "2 3",
        "5",
        3,
        false,
        false);
  }

  /** 分数：通过时取 max(80, 通过率*100)；未通过时取 通过率*60。 */
  private int scoreFor(BossEvaluateResultDto r) {
    if (r.totalTestCases() <= 0) {
      return 0;
    }
    double ratio = (double) r.passedTestCases() / r.totalTestCases();
    if (r.passed()) {
      return Math.max(80, (int) Math.round(ratio * 100));
    }
    return (int) Math.round(ratio * 60);
  }

  /**
   * 缓存条目：完整 {@link BossInternalProblem} + 入库时间，用于 TTL 计算。
   * 暴露包私有供测试 assert。
   */
  record ProblemCacheKey(Long userId, String problemId) {}

  record CachedProblem(BossInternalProblem problem, Instant cachedAt) {}
}
