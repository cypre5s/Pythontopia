package com.pythontopia.world.boss;

import com.pythontopia.common.error.BusinessException;
import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.learner.profile.dto.LearnerProfileSignals;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import com.pythontopia.learner.twin.LearnerTwinService;
import com.pythontopia.mainline.ChapterCatalog;
import com.pythontopia.mainline.test.ChapterProblemBankRepository;
import com.pythontopia.mainline.test.ChapterProblemBankRepository.PreferredCodingQuestion;
import com.pythontopia.submission.judge.JudgeCaseEvaluator;
import com.pythontopia.submission.judge.JudgeTestCase;
import com.pythontopia.submission.judge.JudgeWorkerPool;
import com.pythontopia.submission.judge.StaticCodeChecker;
import com.pythontopia.world.boss.dto.BossAttemptRequest;
import com.pythontopia.world.boss.dto.BossAttemptResultDto;
import com.pythontopia.world.boss.dto.BossEvaluateResultDto;
import com.pythontopia.world.boss.dto.BossInternalProblem;
import com.pythontopia.world.boss.dto.BossPrefetchStatusDto;
import com.pythontopia.world.boss.dto.BossProblemDto;
import com.pythontopia.world.boss.dto.BossProblemRequest;
import com.pythontopia.world.boss.dto.BossProblemRequest.ProfileSignalsPayload;
import com.pythontopia.world.boss.dto.BossTestCasePayload;
import com.pythontopia.world.boss.dto.FrontierPickDto;
import com.pythontopia.world.boss.dto.FrontierSelectRequest;
import com.pythontopia.world.boss.dto.FrontierSelectResultDto;
import com.pythontopia.world.boss.review.ApprovedBossProblem;
import com.pythontopia.world.boss.review.BossProblemReviewRepository;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * Boss 副本服务。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>把 chapter + stage 翻译成默认 KC 范围（与前端 {@code MAIN_QUEST_BOSSES} 表一致）；
 *   <li>调 {@link BossGraphClient} 拉经过审查的题目；上游不可用时返回可重试服务错误；
 *   <li>用进程内 cache 持有完整 {@link BossInternalProblem}（含 hidden testCases）， {@link #evaluateAttempt}
 *       时取出转发给 graph evaluate；
 *   <li>graph 评测失败时返回结构化兜底，提示前端用 Pyodide 校验。
 * </ul>
 *
 * <p>缓存 TTL 30 分钟（覆盖单局 4 阶段时长 + 重连缓冲），超时后未提交的题目失效。
 */
@Service
public class BossService {

  private static final Logger LOG = LoggerFactory.getLogger(BossService.class);
  private static final Duration CACHE_TTL = Duration.ofMinutes(30);
  private static final int CACHE_MAX_ENTRIES = 256;
  private static final int EVAL_TIMEOUT_SEC = 5;
  private static final String ISSUE_ID_PREFIX = "issue-";
  private static final String BOSS_ISSUE_ID_PREFIX = "issue-boss-phase-";

  private final BossGraphClient graphClient;
  private final PassiveProfileService passiveProfileService;
  private final LearnerTwinService learnerTwinService;
  private final ChapterProblemBankRepository chapterProblemBankRepository;
  private final JudgeWorkerPool judgeWorkerPool;
  private final StaticCodeChecker staticCodeChecker;
  private final JudgeCaseEvaluator judgeCaseEvaluator;
  private final EndlessPregenService pregenService;
  private final BossProblemReviewRepository reviewRepository;
  private final ExpeditionRunService expeditionRunService;
  private final ExpeditionScoreService expeditionScoreService;
  private final BossAttemptIdempotencyService attemptIdempotencyService;
  private final ApplicationEventPublisher eventPublisher;
  private final ConcurrentMap<ProblemCacheKey, CachedProblem> cache = new ConcurrentHashMap<>();
  private final Set<ProblemGenerationKey> generationsInFlight = ConcurrentHashMap.newKeySet();
  // shortcut: request retry keys are process-local; persist them before enabling multiple replicas.
  private final ConcurrentMap<ProblemRequestKey, CachedRequestProblem> requestCache =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<ProblemRequestKey, ProblemRequestSignature> requestIdsInFlight =
      new ConcurrentHashMap<>();

  public BossService(
      BossGraphClient graphClient,
      PassiveProfileService passiveProfileService,
      LearnerTwinService learnerTwinService,
      ChapterProblemBankRepository chapterProblemBankRepository,
      JudgeWorkerPool judgeWorkerPool,
      StaticCodeChecker staticCodeChecker,
      JudgeCaseEvaluator judgeCaseEvaluator,
      EndlessPregenService pregenService,
      BossProblemReviewRepository reviewRepository,
      ExpeditionRunService expeditionRunService,
      ExpeditionScoreService expeditionScoreService,
      BossAttemptIdempotencyService attemptIdempotencyService,
      ApplicationEventPublisher eventPublisher) {
    this.graphClient = graphClient;
    this.passiveProfileService = passiveProfileService;
    this.learnerTwinService = learnerTwinService;
    this.chapterProblemBankRepository = chapterProblemBankRepository;
    this.judgeWorkerPool = judgeWorkerPool;
    this.staticCodeChecker = staticCodeChecker;
    this.judgeCaseEvaluator = judgeCaseEvaluator;
    this.pregenService = pregenService;
    this.reviewRepository = reviewRepository;
    this.expeditionRunService = expeditionRunService;
    this.expeditionScoreService = expeditionScoreService;
    this.attemptIdempotencyService = attemptIdempotencyService;
    this.eventPublisher = eventPublisher;
  }

  /**
   * Deterministic KC selection shared with the learner Twin; Graph remains responsible for
   * questions. A qualified KC without a course problem may still be used for same-KC AI generation.
   */
  public FrontierSelectResultDto selectFrontier(Long userId, FrontierSelectRequest request) {
    FrontierSelectResultDto frontier =
        learnerTwinService.frontier(userId, request.focusChapter(), request.exclude(), 40);
    return limitFrontierPicks(frontier.picks(), request.count());
  }

  /**
   * Re-rank only an eligible server-loaded KC to honor an explicit expedition navigation choice.
   */
  public FrontierSelectResultDto selectFrontier(
      Long userId, Integer focusChapter, String preferredKcCode) {
    FrontierSelectResultDto frontier =
        learnerTwinService.frontier(userId, focusChapter, List.of(), 40, preferredKcCode);
    return limitFrontierPicks(
        frontier.picks().stream().filter(pick -> pick.chapter() > 1).toList(), 40);
  }

  private FrontierSelectResultDto limitFrontierPicks(List<FrontierPickDto> picks, int count) {
    if (picks.isEmpty()) {
      throw new BusinessException(
          org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
          "EXPEDITION_NO_PRACTICE_PROBLEMS",
          "当前范围没有可用于 Python 编程的知识点。请先完成主线中的前置小测或练习，或切换到后续章节。");
    }
    return new FrontierSelectResultDto(picks.stream().limit(count).toList());
  }

  public BossProblemDto fetchProblem(int chapter, BossProblemRequest request, Long userId) {
    if (request.requestId() == null) return fetchProblemInternal(chapter, request, userId);
    ProblemRequestKey key = new ProblemRequestKey(userId, request.requestId());
    ProblemRequestSignature signature = new ProblemRequestSignature(chapter, request);
    ProblemRequestSignature existing = requestIdsInFlight.putIfAbsent(key, signature);
    if (existing != null) {
      if (!existing.equals(signature)) throw requestIdConflict();
      throw generationPending();
    }
    try {
      return fetchProblemInternal(chapter, request, userId);
    } finally {
      requestIdsInFlight.remove(key, signature);
    }
  }

  private BossProblemDto fetchProblemInternal(
      int chapter, BossProblemRequest request, Long userId) {
    // preferredKc 优先级高于旧 kcCode（前端 ArenaPage 走 preferredKc）。
    String effectiveKc = request.preferredKc() != null ? request.preferredKc() : request.kcCode();
    String kc = resolveKc(chapter, effectiveKc);
    ExpeditionRequestBinding expeditionBinding =
        validateExpeditionRun(
            userId, request.expeditionRunId(), request.expeditionNodeId(), chapter, false, kc);
    String expeditionRunId = expeditionBinding.runId();
    if (expeditionRunId != null) {
      Optional<BossInternalProblem> open =
          pregenService.findOpenExpeditionProblem(
              userId, expeditionRunId, request.expeditionNodeId(), expeditionBinding.bossPhase());
      if (open != null && open.isPresent()) {
        BossInternalProblem problem = open.orElseThrow();
        requireProblemContext(problem, chapter, request.stage(), kc);
        if (isMasteryEligible(problem)) {
          cacheProblem(userId, problem);
          return problem.toPublic();
        }
        LOG.warn(
            "boss skipped revoked AI problemId={} user={} run={}",
            problem.problemId(),
            userId,
            expeditionRunId);
      }
    }
    int difficulty = computeTargetDifficulty(userId, kc, request.stage());
    ProblemGenerationKey generationKey =
        new ProblemGenerationKey(
            userId,
            chapter,
            request.stage(),
            kc,
            difficulty,
            request.preferredKind(),
            request.arenaLevel(),
            expeditionRunId,
            request.expeditionNodeId());
    CachedRequestProblem cachedRequest = findCachedRequest(userId, request.requestId());
    if (cachedRequest != null) {
      if (!cachedRequest.generationKey().equals(generationKey)) {
        throw requestIdConflict();
      }
      BossInternalProblem cachedProblem =
          Optional.ofNullable(cache.get(new ProblemCacheKey(userId, cachedRequest.problemId())))
              .map(CachedProblem::problem)
              .orElseGet(
                  () ->
                      pregenService
                          .findByProblemId(userId, cachedRequest.problemId())
                          .orElse(null));
      if (cachedProblem != null) {
        cacheProblem(userId, cachedProblem);
        return cachedProblem.toPublic();
      }
      requestCache.remove(new ProblemRequestKey(userId, request.requestId()), cachedRequest);
    }
    Set<String> issuedFingerprints =
        expeditionRunId == null
            ? Set.of()
            : expeditionRunService.findIssuedQuestionFingerprints(userId, expeditionRunId);

    // 1) 复用「已预生成未做」(READY) 的同维度题：命中即原子标 CONSUMED，省去现场等 LLM。
    //    这条同时实现「学生中途退出，下次登入复用已生成未做的题」——上次会话预取留下的 READY 行。
    Optional<BossInternalProblem> reused =
        expeditionRunId == null
            ? pregenService.claimReady(
                userId,
                chapter,
                request.stage(),
                kc,
                difficulty,
                request.preferredKind(),
                request.arenaLevel(),
                null)
            : pregenService.claimReady(
                userId,
                chapter,
                request.stage(),
                kc,
                difficulty,
                request.preferredKind(),
                request.arenaLevel(),
                expeditionRunId,
                request.expeditionNodeId());
    if (reused.isPresent()) {
      BossInternalProblem p = reused.get();
      // 展示时套用「本次请求」的 stage + 本次推断难度：预取时的 stage/难度不串到当前上下文
      // （也避免共享端点下复用题把异常 stage 显示给主线 boss）。
      requireProblemContext(p, chapter, request.stage(), kc);
      if (!isMasteryEligible(p)) {
        LOG.warn(
            "boss skipped revoked AI problemId={} user={} run={}",
            p.problemId(),
            userId,
            expeditionRunId);
      } else {
        if (expeditionRunId != null
            && pregenService.isOpenExpeditionProblem(
                userId,
                expeditionRunId,
                request.expeditionNodeId(),
                p.problemId(),
                expeditionBinding.bossPhase())) {
          return cacheAndRemember(userId, request, generationKey, p);
        }
        if (!issuedFingerprints.contains(questionFingerprint(p))) {
          cacheProblem(userId, p);
          LOG.info(
              "boss reuse pregenerated problemId={} user={} ch={}", p.problemId(), userId, chapter);
          return rememberRequest(userId, request, generationKey, p);
        }
        LOG.info(
            "boss skipped repeated pregenerated question user={} run={} ch={}",
            userId,
            expeditionRunId,
            chapter);
      }
    }

    // 2) 没有该学生的 READY 个性化题时，再查教师已通过的全局题库：按
    //    chapter/stage/KC/kind 只读选择最接近目标难度的版本。题库 payload 含 hidden tests，
    //    只在后端回灌缓存并为当前学生落判题兜底，前端仅拿公开投影。
    Optional<ApprovedBossProblem> approved =
        reviewRepository.findApproved(
            chapter, request.stage(), kc, request.preferredKind(), difficulty);
    if (approved.isPresent()) {
      BossInternalProblem source = approved.get().problem();
      requireProblemContext(source, chapter, request.stage(), kc);
      if (!issuedFingerprints.contains(questionFingerprint(source))) {
        BossInternalProblem problem =
            source.issueAs(issueProblemId(expeditionBinding.bossPhase()), kc);
        BossInternalProblem persisted =
            persistConsumed(userId, chapter, kc, request, difficulty, problem, expeditionRunId);
        if (persisted != null) problem = persisted;
        cacheProblem(userId, problem);
        LOG.info(
            "boss selected approved review={} version={} problemId={} user={} ch={}",
            approved.get().reviewId(),
            approved.get().version(),
            problem.problemId(),
            userId,
            chapter);
        return rememberRequest(userId, request, generationKey, problem);
      }
    }

    BossPrefetchStatusDto prefetchStatus =
        expeditionRunId == null
            ? pregenService.getPrefetchStatus(
                userId,
                chapter,
                request.stage(),
                kc,
                difficulty,
                request.preferredKind(),
                request.arenaLevel(),
                null)
            : pregenService.getPrefetchStatus(
                userId,
                chapter,
                request.stage(),
                kc,
                difficulty,
                request.preferredKind(),
                request.arenaLevel(),
                expeditionRunId,
                request.expeditionNodeId());
    if (prefetchStatus != null && "PENDING".equals(prefetchStatus.status())) {
      throw new com.pythontopia.common.error.BusinessException(
          org.springframework.http.HttpStatus.CONFLICT, "BOSS_PREFETCH_PENDING", "题目仍在生成，请稍后重试");
    }

    // 3) 个性化预生成与 approved bank 均未命中 → 现场走 graph 出题。
    if (!generationsInFlight.add(generationKey)) {
      throw generationPending();
    }
    try {
      BossInternalProblem fromGraph;
      try {
        fromGraph =
            graphClient.requestProblem(
                userId,
                chapter,
                request.stage(),
                kc,
                difficulty,
                request.preferredKc(),
                request.preferredKind(),
                authoritativeSignals(userId, request.profileSignals()));
      } catch (RestClientException ex) {
        LOG.warn(
            "graph boss problem unavailable ({}): {}",
            ex.getClass().getSimpleName(),
            ex.getMessage());
        Optional<BossInternalProblem> golden =
            findGoldenProblem(kc, chapter, request.stage(), difficulty, issuedFingerprints);
        if (golden.isEmpty()) {
          throw new com.pythontopia.common.error.BusinessException(
              org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
              "BOSS_PROBLEM_GENERATION_UNAVAILABLE",
              "题目生成服务暂不可用，当前知识点也没有可用的课程题库题目，请稍后重试");
        }
        fromGraph = golden.get();
      }
      if (fromGraph == null || fromGraph.problemId() == null) {
        fromGraph =
            findGoldenProblem(kc, chapter, request.stage(), difficulty, issuedFingerprints)
                .orElse(null);
        if (fromGraph == null) {
          throw new com.pythontopia.common.error.BusinessException(
              org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
              "BOSS_PROBLEM_GENERATION_UNAVAILABLE",
              "题目生成服务暂不可用，当前知识点也没有可用的课程题库题目，请稍后重试");
        }
      }
      requireProblemContext(fromGraph, chapter, request.stage(), kc);
      if (issuedFingerprints.contains(questionFingerprint(fromGraph))) {
        fromGraph =
            findGoldenProblem(kc, chapter, request.stage(), difficulty, issuedFingerprints)
                .orElseThrow(() -> repeatedProblemUnavailable());
      }
      if (fromGraph.aiGenerated()) {
        boolean autoApproved = false;
        if (fromGraph.reviewed()) {
          try {
            autoApproved =
                reviewRepository.approveAutomaticallyIfNotDuplicate(
                        userId,
                        request.preferredKind(),
                        chapter,
                        request.stage(),
                        kc,
                        difficulty,
                        fromGraph)
                    || reviewRepository.isApproved(fromGraph);
          } catch (DataAccessException ex) {
            LOG.warn(
                "could not verify generated Boss problem approval: {}",
                ex.getClass().getSimpleName());
          }
        }
        if (!autoApproved) {
          fromGraph =
              findGoldenProblem(kc, chapter, request.stage(), difficulty, issuedFingerprints)
                  .orElseThrow(
                      () ->
                          new com.pythontopia.common.error.BusinessException(
                              org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                              "BOSS_PROBLEM_GENERATION_UNAVAILABLE",
                              "AI 题目尚未通过审核，当前知识点没有可用的课程题库题目，请稍后重试"));
        }
      }
      BossInternalProblem issued =
          fromGraph.issueAs(issueProblemId(expeditionBinding.bossPhase()), kc);
      // 现场题也落库 (CONSUMED)，供内存缓存过期 / 进程重启后判题兜底取回 testCases（best-effort）。
      BossInternalProblem persisted =
          persistConsumed(userId, chapter, kc, request, difficulty, issued, expeditionRunId);
      if (persisted != null) issued = persisted;
      return cacheAndRemember(userId, request, generationKey, issued);
    } finally {
      generationsInFlight.remove(generationKey);
    }
  }

  private Optional<BossInternalProblem> findGoldenProblem(
      String kcCode, int chapter, int stage, int difficulty, Set<String> excludedFingerprints) {
    return chapterProblemBankRepository.findByKcCodes(List.of(kcCode)).stream()
        .filter(question -> kcCode.equals(question.kcCode()))
        .map(question -> toGoldenProblem(question, chapter, stage, difficulty))
        .filter(problem -> !excludedFingerprints.contains(questionFingerprint(problem)))
        .findFirst();
  }

  private com.pythontopia.common.error.BusinessException repeatedProblemUnavailable() {
    return new com.pythontopia.common.error.BusinessException(
        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
        "BOSS_REPEAT_PROBLEM_UNAVAILABLE",
        "当前知识点暂时没有新的可用题目，请稍后重试");
  }

  private static BossInternalProblem toGoldenProblem(
      PreferredCodingQuestion question, int chapter, int stage, int difficulty) {
    List<BossTestCasePayload> cases =
        question.hiddenTestCases().stream()
            .map(testCase -> new BossTestCasePayload(testCase.input(), testCase.output(), false))
            .toList();
    var sample = question.samples().getFirst();
    return new BossInternalProblem(
        "course-problem-" + question.sourceProblemId(),
        chapter,
        stage,
        difficulty,
        question.kcCode(),
        "课程题库 · " + question.title() + "\n\n" + question.statementMarkdown(),
        List.of("先读清输入与输出格式，再用样例检查程序。"),
        sample.input(),
        sample.output(),
        cases.size(),
        true,
        false,
        cases,
        question.referenceSolution());
  }

  /**
   * 触发后台预生成「下一题」：按同维度 + 当前难度异步生成并落 {@code pregenerated_problem} (READY)。 立即返回带 job id 的
   * PENDING/READY，真正生成在 {@link EndlessPregenService#generatePrefetch} 的 {@code @Async} 线程进行；失败落
   * FAILED，可重试。
   */
  public BossPrefetchStatusDto prefetch(int chapter, BossProblemRequest request, Long userId) {
    String effectiveKc = request.preferredKc() != null ? request.preferredKc() : request.kcCode();
    String kc = resolveKc(chapter, effectiveKc);
    String expeditionRunId =
        validateExpeditionRun(
                userId, request.expeditionRunId(), request.expeditionNodeId(), chapter, true, kc)
            .runId();
    int difficulty = computeTargetDifficulty(userId, kc, request.stage());
    EndlessPregenService.PrefetchStart start =
        expeditionRunId == null
            ? pregenService.beginPrefetch(
                userId,
                chapter,
                request.stage(),
                kc,
                difficulty,
                request.preferredKind(),
                request.arenaLevel(),
                null)
            : pregenService.beginPrefetch(
                userId,
                chapter,
                request.stage(),
                kc,
                difficulty,
                request.preferredKind(),
                request.arenaLevel(),
                expeditionRunId,
                request.expeditionNodeId());
    if (start.shouldGenerate() && start.status().jobId() != null) {
      try {
        if (expeditionRunId == null) {
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
              null,
              authoritativeSignals(userId, request.profileSignals()),
              start.status().attempts());
        } else {
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
              request.expeditionNodeId(),
              authoritativeSignals(userId, request.profileSignals()),
              start.status().attempts());
        }
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
    return prefetchStatus(
        chapter,
        stage,
        preferredKc,
        requestedDifficulty,
        preferredKind,
        arenaLevel,
        expeditionRunId,
        null,
        userId);
  }

  public BossPrefetchStatusDto prefetchStatus(
      int chapter,
      Integer stage,
      String preferredKc,
      Integer requestedDifficulty,
      String preferredKind,
      String arenaLevel,
      String expeditionRunId,
      String expeditionNodeId,
      Long userId) {
    String kc = resolveKc(chapter, preferredKc);
    int difficulty = computeTargetDifficulty(userId, kc, stage == null ? 1 : stage);
    String activeRunId =
        validateExpeditionRun(userId, expeditionRunId, expeditionNodeId, chapter, true, kc).runId();
    return activeRunId == null
        ? pregenService.getPrefetchStatus(
            userId, chapter, stage, kc, difficulty, preferredKind, arenaLevel, null)
        : pregenService.getPrefetchStatus(
            userId,
            chapter,
            stage,
            kc,
            difficulty,
            preferredKind,
            arenaLevel,
            activeRunId,
            expeditionNodeId);
  }

  /**
   * 出题画像以服务端 unified dimensions 为权威，避免客户端遗漏/篡改导致信号漂移；只有匿名调用 才沿用请求内画像。访谈 metadata
   * 是自评快照，不替代下方正式掌握度与练习历史。
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
        requestSignals == null ? null : requestSignals.questPreferredKind(),
        profile.major(),
        profile.learningGoalKind(),
        profile.targetKcCodes(),
        profile.fieldDependent(),
        profile.sequentialVsHolistic(),
        profile.sessionMinutesPerDay());
  }

  private BossInternalProblem persistConsumed(
      Long userId,
      int chapter,
      String kcCode,
      BossProblemRequest request,
      int difficulty,
      BossInternalProblem problem,
      String expeditionRunId) {
    if (expeditionRunId == null) {
      return pregenService.persistConsumed(
          userId,
          chapter,
          kcCode,
          request.preferredKind(),
          request.arenaLevel(),
          difficulty,
          problem,
          null);
    } else {
      return pregenService.persistConsumed(
          userId,
          chapter,
          kcCode,
          request.preferredKind(),
          request.arenaLevel(),
          difficulty,
          problem,
          expeditionRunId,
          request.expeditionNodeId());
    }
  }

  public BossAttemptResultDto evaluateAttempt(BossAttemptRequest request, Long userId) {
    if (userId != null && request.attemptId() == null) {
      throw new com.pythontopia.common.error.BusinessException(
          org.springframework.http.HttpStatus.BAD_REQUEST,
          "BOSS_ATTEMPT_ID_REQUIRED",
          "判题请求必须包含 attemptId");
    }
    return evaluateAttemptInternal(request, userId);
  }

  private BossAttemptResultDto evaluateAttemptInternal(BossAttemptRequest request, Long userId) {
    BossAttemptIdempotencyService.Claim claim = null;
    Optional<EndlessPregenService.ExpeditionProblemBinding> expeditionBinding =
        pregenService.findExpeditionBinding(userId, request.problemId());
    Integer bossPhaseForAttempt = null;
    if (expeditionBinding.isPresent()) {
      EndlessPregenService.ExpeditionProblemBinding binding = expeditionBinding.get();
      if (binding.nodeId() == null
          || binding.questionFingerprint() == null
          || binding.chapter() != request.chapter()
          || binding.stage() != request.stage()) {
        throw problemBindingMismatch();
      }
      if (!expeditionRunService.isActiveRun(userId, binding.runId())) {
        throw expiredExpeditionRun();
      }
      Integer currentBossPhase =
          expeditionRunService.validateProblemRequest(
              userId,
              binding.runId(),
              binding.nodeId(),
              binding.chapter(),
              false,
              binding.kcCode());
      boolean legacyPhaseOne =
          currentBossPhase != null
              && currentBossPhase == 1
              && binding.bossPhase() == null
              && isLegacyIssueProblemId(request.problemId());
      if (!Objects.equals(currentBossPhase, binding.bossPhase()) && !legacyPhaseOne) {
        throw problemBindingMismatch();
      }
      bossPhaseForAttempt = binding.bossPhase() == null ? currentBossPhase : binding.bossPhase();
    } else if (pregenService.findExpeditionRunId(userId, request.problemId()).isPresent()) {
      // Old rows may have a run id but cannot safely identify which map node issued the question.
      throw problemBindingMismatch();
    }
    // 判题取题：先内存缓存（TTL 内有效），过期 / 缺失再回退 pregenerated_problem 凭 problemId 取回，
    // 修复「内存缓存过期 / 进程重启 → 判题失效」。
    BossInternalProblem problem = resolveProblemForEval(request.problemId(), userId);
    if (problem == null) {
      LOG.info("boss attempt problemId={} not issued to user={}", request.problemId(), userId);
      return new BossAttemptResultDto(
          request.problemId(), false, 0, 3, null, 0, "题目缓存过期且无持久化记录；请重新生成题目后再提交。");
    }
    if (userId != null) {
      claim = attemptIdempotencyService.claim(userId, request.attemptId(), request);
      if (claim.replay() != null) return claim.replay();
    }
    try {
      requireProblemContext(problem, request.chapter(), request.stage());
      if (expeditionBinding.isPresent()
          && !Objects.equals(
              expeditionBinding.get().questionFingerprint(), questionFingerprint(problem))) {
        throw problemBindingMismatch();
      }
      List<BossTestCasePayload> testCases = problem.testCases();
      if (testCases.isEmpty()) {
        LOG.warn("problem {} has empty testCases (graph schema drift?)", request.problemId());
        return completeAttempt(
            request,
            userId,
            claim,
            new BossAttemptResultDto(
                request.problemId(),
                false,
                0,
                problem.totalTestCases(),
                null,
                0,
                "题目缓存数据异常（缺 testCases），请重试生成题目。"),
            null);
      }
      BossEvaluateResultDto graphResult;
      try {
        graphResult =
            graphClient.evaluateAttempt(
                request.problemId(), request.code(), testCases, EVAL_TIMEOUT_SEC);
        if (graphResult == null) throw new RestClientException("graph evaluate returned null body");
        if ("sandbox_unavailable".equals(graphResult.runtimeError())) {
          LOG.warn("graph sandbox unavailable; using the existing local Python judge");
          graphResult = evaluateLocally(request, testCases);
        }
      } catch (RestClientException ex) {
        LOG.warn(
            "graph evaluate failed ({}): {}; using the existing local Python judge",
            ex.getClass().getSimpleName(),
            ex.getMessage());
        graphResult = evaluateLocally(request, testCases);
      }
      int score = scoreFor(graphResult);
      boolean masteryEligible = isMasteryEligible(problem);
      String runId = expeditionBinding.isPresent() ? expeditionBinding.get().runId() : null;
      String attemptFingerprint =
          expeditionBinding.isPresent()
              ? expeditionAttemptFingerprint(
                  questionFingerprint(problem),
                  expeditionBinding.get().nodeId(),
                  bossPhaseForAttempt)
              : questionFingerprint(problem);
      ExpeditionScoreService.VerifiedAttemptResult attemptResult =
          recordVerifiedExpeditionAttempt(
              userId, runId, problem, attemptFingerprint, graphResult, score, masteryEligible);
      if (!attemptResult.accepted()) {
        if (!expeditionRunService.isActiveRun(userId, runId)) {
          throw expiredExpeditionRun();
        }
        throw new com.pythontopia.common.error.BusinessException(
            org.springframework.http.HttpStatus.CONFLICT,
            "EXPEDITION_ATTEMPT_LIMIT_REACHED",
            "本局可计分判题次数已用完");
      }
      String feedback =
          graphResult.passed()
              ? "通过 ✅ 服务端沙箱已确认。"
              : graphResult.runtimeError() != null
                  ? "代码运行错误：" + graphResult.runtimeError()
                  : "未通过 ❌ 部分用例失败，请参考下方 hint。";
      boolean publishLearningEvent =
          attemptResult.publishLearningEvent() && masteryEligible;
      if (problem.aiGenerated() && !masteryEligible) {
        feedback += " 本题尚未确认经教师审核通过，不计入本局进度、排行榜与知识画像。";
      }
      // 闭环关键：服务端判定成功且题目已获准用于画像时才发学习事件；待教师审核的 AI 题只练习、不计掌握度。
      // 事件与 SubmissionService 共用并驱动 KC 掌握度（测）+ 8 维画像被动修订；缓存过期 / 缺 testCases / graph 不可用等「未判定」路径不发，
      // 避免把非判题结果污染进掌握度；web 重放由 attemptId 结果记录阻止二次发事件。
      return completeAttempt(
          request,
          userId,
          claim,
          new BossAttemptResultDto(
              request.problemId(),
              graphResult.passed(),
              graphResult.passedTestCases(),
              graphResult.totalTestCases(),
              graphResult.failureFirstHint(),
              score,
              feedback),
          publishLearningEvent
              ? practiceOutcomeEvent(userId, problem, graphResult, request.attemptId())
              : null);
    } catch (RuntimeException exception) {
      if (claim != null) {
        attemptIdempotencyService.release(userId, request.attemptId(), claim);
      }
      throw exception;
    }
  }

  private boolean isMasteryEligible(BossInternalProblem problem) {
    if (!problem.aiGenerated()) {
      return true;
    }
    try {
      return problem.reviewed() && reviewRepository.isApproved(problem);
    } catch (DataAccessException exception) {
      LOG.warn(
          "could not verify generated Boss problem approval; skip mastery evidence: {}",
          exception.getClass().getSimpleName());
      return false;
    }
  }

  private BossEvaluateResultDto evaluateLocally(
      BossAttemptRequest request, List<BossTestCasePayload> testCases) {
    var check = judgeWorkerPool.submitAndWait(() -> staticCodeChecker.check(request.code()));
    if (check.systemError()) throw evaluatorUnavailable();
    if (!check.passed()) {
      return new BossEvaluateResultDto(
          request.problemId(), false, 0, testCases.size(), check.failureReason(), null, List.of(0));
    }
    List<JudgeTestCase> cases =
        testCases.stream()
            .map(
                testCase ->
                    new JudgeTestCase(
                        testCase.input(), testCase.expectedOutput(), 5000, !testCase.isSample()))
            .toList();
    var result =
        judgeWorkerPool.submitAndWait(() -> judgeCaseEvaluator.evaluate(request.code(), cases));
    if (result.finalVerdict() == com.pythontopia.submission.dto.Verdict.SYSTEM_ERROR) {
      throw evaluatorUnavailable();
    }
    boolean passed = result.finalVerdict() == com.pythontopia.submission.dto.Verdict.AC;
    String runtimeError =
        result.finalVerdict() == com.pythontopia.submission.dto.Verdict.RE
            ? result.failureReason()
            : null;
    return new BossEvaluateResultDto(
        request.problemId(),
        passed,
        result.passedCases(),
        cases.size(),
        result.failureReason(),
        runtimeError,
        passed ? List.of() : List.of(result.passedCases()));
  }

  private static com.pythontopia.common.error.BusinessException evaluatorUnavailable() {
    return new com.pythontopia.common.error.BusinessException(
        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
        "BOSS_EVALUATOR_UNAVAILABLE",
        "本地判题环境暂不可用，此次提交未计入结果。");
  }

  private BossAttemptResultDto completeAttempt(
      BossAttemptRequest request,
      Long userId,
      BossAttemptIdempotencyService.Claim claim,
      BossAttemptResultDto result,
      LearningEvent event) {
    if (claim != null) {
      return attemptIdempotencyService.complete(userId, request.attemptId(), claim, result, event);
    }
    if (event != null) {
      eventPublisher.publishEvent(event);
    }
    return result;
  }

  private ExpeditionRequestBinding validateExpeditionRun(
      Long userId, String runId, String nodeId, int chapter, boolean allowPrefetch, String kcCode) {
    if (runId == null) {
      if (nodeId != null) throw problemBindingMismatch();
      return new ExpeditionRequestBinding(null, null);
    }
    Integer bossPhase =
        expeditionRunService.validateProblemRequest(
            userId, runId, nodeId, chapter, allowPrefetch, kcCode);
    return new ExpeditionRequestBinding(runId, bossPhase);
  }

  static String issueProblemId(Integer bossPhase) {
    if (bossPhase == null) return ISSUE_ID_PREFIX + UUID.randomUUID();
    if (bossPhase < 1 || bossPhase > 3) {
      throw new IllegalArgumentException("boss phase must be in 1..3");
    }
    return BOSS_ISSUE_ID_PREFIX + bossPhase + "-" + UUID.randomUUID();
  }

  static Integer bossPhaseFromProblemId(String problemId) {
    if (problemId == null || !problemId.startsWith(BOSS_ISSUE_ID_PREFIX)) return null;
    int separator = problemId.indexOf('-', BOSS_ISSUE_ID_PREFIX.length());
    if (separator < 0) return null;
    try {
      int phase = Integer.parseInt(problemId.substring(BOSS_ISSUE_ID_PREFIX.length(), separator));
      UUID.fromString(problemId.substring(separator + 1));
      return phase >= 1 && phase <= 3 ? phase : null;
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }

  static boolean isLegacyIssueProblemId(String problemId) {
    if (problemId == null
        || !problemId.startsWith(ISSUE_ID_PREFIX)
        || problemId.startsWith(BOSS_ISSUE_ID_PREFIX)) {
      return false;
    }
    try {
      UUID.fromString(problemId.substring(ISSUE_ID_PREFIX.length()));
      return true;
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  static String expeditionAttemptFingerprint(
      String questionFingerprint, String nodeId, Integer bossPhase) {
    if (bossPhase == null) return questionFingerprint;
    if (nodeId == null || bossPhase < 1 || bossPhase > 3) {
      throw new IllegalArgumentException("boss attempt binding is invalid");
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      updateFingerprint(digest, questionFingerprint);
      updateFingerprint(digest, nodeId);
      updateFingerprint(digest, Integer.toString(bossPhase));
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private com.pythontopia.common.error.BusinessException problemBindingMismatch() {
    return new com.pythontopia.common.error.BusinessException(
        org.springframework.http.HttpStatus.CONFLICT,
        "EXPEDITION_PROBLEM_BINDING_MISMATCH",
        "题目与当前远征节点绑定不匹配，无法判题或结算");
  }

  private ExpeditionScoreService.VerifiedAttemptResult recordVerifiedExpeditionAttempt(
      Long userId,
      String runId,
      BossInternalProblem problem,
      String attemptFingerprint,
      BossEvaluateResultDto result,
      int score,
      boolean masteryEligible) {
    if (userId == null || runId == null) {
      return new ExpeditionScoreService.VerifiedAttemptResult(true, true);
    }
    if (!masteryEligible) {
      return new ExpeditionScoreService.VerifiedAttemptResult(true, false);
    }
    return expeditionScoreService.recordVerifiedAttempt(
        userId, runId, problem.problemId(), attemptFingerprint, result.passed(), score);
  }

  private record ExpeditionRequestBinding(String runId, Integer bossPhase) {}

  static String questionFingerprint(BossInternalProblem problem) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      updateFingerprint(digest, problem.kcCode());
      updateFingerprint(digest, problem.statement());
      updateFingerprint(digest, problem.sampleInput());
      updateFingerprint(digest, problem.sampleOutput());
      var testCases =
          problem.testCases().stream()
              .sorted(
                  Comparator.comparing(BossTestCasePayload::input)
                      .thenComparing(BossTestCasePayload::expectedOutput))
              .toList();
      updateFingerprint(digest, Integer.toString(testCases.size()));
      for (BossTestCasePayload testCase : testCases) {
        updateFingerprint(digest, testCase.input());
        updateFingerprint(digest, testCase.expectedOutput());
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static void updateFingerprint(MessageDigest digest, String value) {
    byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
    digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
    digest.update(bytes);
  }

  private void requireProblemContext(BossInternalProblem problem, int chapter, int stage) {
    requireProblemContext(problem, chapter, stage, null);
  }

  private void requireProblemContext(
      BossInternalProblem problem, int chapter, int stage, String expectedKcCode) {
    if (problem.chapter() != chapter
        || problem.stage() != stage
        || (expectedKcCode != null && !Objects.equals(problem.kcCode(), expectedKcCode))) {
      throw new com.pythontopia.common.error.BusinessException(
          org.springframework.http.HttpStatus.CONFLICT,
          "BOSS_PROBLEM_CONTEXT_MISMATCH",
          "题目章节、阶段或知识点与签发内容不一致");
    }
  }

  private com.pythontopia.common.error.BusinessException expiredExpeditionRun() {
    return new com.pythontopia.common.error.BusinessException(
        org.springframework.http.HttpStatus.CONFLICT, "EXPEDITION_RUN_EXPIRED", "远征存档已变化，请刷新后重试");
  }

  /**
   * 把可用于画像的 boss / endless 判题结果发布为 {@link LearningEvent}：通过 → {@code PracticeSucceeded}， 未通过 →
   * {@code PracticeFailed}（verdict 取 RE/WA），与 {@code SubmissionService} 同一套事件， 统一驱动 KC 掌握度与 8
   * 维画像被动修订。课程题和教师已批准的 AI 题可计入；待审核 AI 题可练习但不更新能力画像。
   *
   * <p>boss 题为 AI 现场生成、无 {@code course_problem} 数字主键，故事件 {@code problemId} 传 {@code null} （各监听器对
   * null 均安全）；难度按题目 1..5 映射到 mastery 可识别的档位字符串，保留难度加权语义。 userId 为空（匿名/未鉴权）或题目缺 kcCode
   * 时安全跳过，绝不抛错影响判题返回。
   */
  private LearningEvent practiceOutcomeEvent(
      Long userId, BossInternalProblem problem, BossEvaluateResultDto result, UUID attemptId) {
    if (userId == null) {
      return null;
    }
    String kcCode = problem.kcCode();
    if (kcCode == null || kcCode.isBlank()) {
      LOG.warn("boss attempt problemId={} has no kcCode; skip LearningEvent", problem.problemId());
      return null;
    }
    String difficulty = difficultyBand(problem.difficulty());
    String idempotencyKey = "boss-attempt:" + userId + ":" + attemptId;
    if (result.passed()) {
      return new LearningEvent.PracticeSucceeded(
          userId, kcCode, null, difficulty, null, idempotencyKey);
    }
    String verdict =
        result.runtimeError() != null && !result.runtimeError().isBlank() ? "RE" : "WA";
    return new LearningEvent.PracticeFailed(
        userId, kcCode, null, verdict, difficulty, null, idempotencyKey);
  }

  /**
   * Boss 难度 int(1..5) → mastery 难度系数可识别的档位字符串（驱动难度加权：易题失败重罚、难题通过厚奖， 见 {@code
   * MasteryProperties.difficultyMultiplier}）。1..2→easy，3→medium，4..5→hard。
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
   * 取判题所需完整题（含 hidden testCases）：先查进程内缓存（TTL 内有效）；过期 / 未命中只从 {@code pregenerated_problem} 按 user +
   * issued problemId 取回。审核题在发放前会先签发用户专属 ID 并写入该表；全局审核库 ID 不可直接用于判题。未找到用户记录即返回 {@code null}。
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
      cacheProblem(userId, fromDb); // 回灌缓存避免重复查库
    }
    return fromDb;
  }

  /** 缓存条目含隐藏测试；所有写入共用此处，严格限制容量并优先清理最旧项。 */
  private synchronized void cacheProblem(Long userId, BossInternalProblem problem) {
    cache.put(
        new ProblemCacheKey(userId, problem.problemId()),
        new CachedProblem(problem, Instant.now()));
    Instant now = Instant.now();
    cache
        .entrySet()
        .removeIf(e -> Duration.between(e.getValue().cachedAt(), now).compareTo(CACHE_TTL) > 0);
    while (cache.size() > CACHE_MAX_ENTRIES) {
      Map.Entry<ProblemCacheKey, CachedProblem> oldest =
          cache.entrySet().stream()
              .min(Comparator.comparing(entry -> entry.getValue().cachedAt()))
              .orElse(null);
      if (oldest == null) return;
      cache.remove(oldest.getKey(), oldest.getValue());
    }
  }

  private BossProblemDto cacheAndRemember(
      Long userId,
      BossProblemRequest request,
      ProblemGenerationKey generationKey,
      BossInternalProblem problem) {
    cacheProblem(userId, problem);
    return rememberRequest(userId, request, generationKey, problem);
  }

  private BossProblemDto rememberRequest(
      Long userId,
      BossProblemRequest request,
      ProblemGenerationKey generationKey,
      BossInternalProblem problem) {
    if (request.requestId() != null) {
      synchronized (requestCache) {
        Instant now = Instant.now();
        requestCache
            .entrySet()
            .removeIf(
                entry ->
                    Duration.between(entry.getValue().cachedAt(), now).compareTo(CACHE_TTL) > 0);
        requestCache.put(
            new ProblemRequestKey(userId, request.requestId()),
            new CachedRequestProblem(generationKey, problem.problemId(), now));
        while (requestCache.size() > CACHE_MAX_ENTRIES) {
          Map.Entry<ProblemRequestKey, CachedRequestProblem> oldest =
              requestCache.entrySet().stream()
                  .min(Comparator.comparing(entry -> entry.getValue().cachedAt()))
                  .orElse(null);
          if (oldest == null) break;
          requestCache.remove(oldest.getKey(), oldest.getValue());
        }
      }
    }
    return problem.toPublic();
  }

  private CachedRequestProblem findCachedRequest(Long userId, String requestId) {
    if (requestId == null) return null;
    ProblemRequestKey key = new ProblemRequestKey(userId, requestId);
    CachedRequestProblem cached = requestCache.get(key);
    if (cached != null
        && Duration.between(cached.cachedAt(), Instant.now()).compareTo(CACHE_TTL) > 0) {
      requestCache.remove(key, cached);
      return null;
    }
    return cached;
  }

  private com.pythontopia.common.error.BusinessException requestIdConflict() {
    return new com.pythontopia.common.error.BusinessException(
        org.springframework.http.HttpStatus.CONFLICT,
        "BOSS_REQUEST_ID_CONFLICT",
        "出题请求标识已用于其他题目条件，请重新开始");
  }

  private com.pythontopia.common.error.BusinessException generationPending() {
    return new com.pythontopia.common.error.BusinessException(
        org.springframework.http.HttpStatus.CONFLICT,
        "BOSS_PROBLEM_GENERATION_PENDING",
        "题目仍在生成，请稍后重试");
  }

  /** Boss 目标难度复用 Twin 的 KC 规则；数据缺失或聚合故障时回到 stage 难度。 */
  int computeTargetDifficulty(Long userId, String anchorKc, int stage) {
    int fallback = clampDifficulty(stage);
    if (userId == null || anchorKc == null) return fallback;
    try {
      Integer recommended = learnerTwinService.recommendedDifficulty(userId, anchorKc);
      return recommended == null ? fallback : clampDifficulty(recommended);
    } catch (RuntimeException ex) {
      LOG.debug("boss target difficulty degraded for user {}: {}", userId, ex.getMessage());
      return fallback;
    }
  }

  private static int clampDifficulty(int difficulty) {
    return Math.min(5, Math.max(1, difficulty));
  }

  /**
   * 章号 1..7 → 默认锚定 KC（该章起始 KC code）。权威映射统一走 {@link ChapterCatalog} （与 {@code
   * data/course-pack-43/json/course_kc.json} 对齐：ch1=001..007、ch2=008..016、
   * ch3=017..030、ch4=031..038、ch5=039..047、ch6=048..053、ch7=054..065）。
   *
   * <p>修复旧硬编码表 ch2..7 整体「错位一章」的缺陷（如旧值 ch2→KC-006、ch3→KC-016 实际分别属于 第 1、2 章），该错位会让 boss
   * 出题与学情聚合锚到错误章节的 KC。不再臆造 KC，全部源自课程包。
   */
  String resolveKc(int chapter, String override) {
    int safeChapter = ChapterCatalog.isValidChapter(chapter) ? chapter : 1;
    if (override != null && !override.isBlank()) {
      String normalized = override.trim();
      if (ChapterCatalog.chapterOfKc(normalized)
          .filter(kcChapter -> kcChapter == safeChapter)
          .isEmpty()) {
        throw new com.pythontopia.common.error.BusinessException(
            org.springframework.http.HttpStatus.BAD_REQUEST,
            "INVALID_BOSS_KC",
            "KC 必须属于指定章节的 Python 语言基础课程内容");
      }
      return normalized;
    }
    return ChapterCatalog.kcCodes(safeChapter).get(0);
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

  /** 缓存条目：完整 {@link BossInternalProblem} + 入库时间，用于 TTL 计算。 暴露包私有供测试 assert。 */
  record ProblemCacheKey(Long userId, String problemId) {}

  record ProblemRequestKey(Long userId, String requestId) {}

  record ProblemRequestSignature(int chapter, BossProblemRequest request) {}

  record CachedRequestProblem(
      ProblemGenerationKey generationKey, String problemId, Instant cachedAt) {}

  record ProblemGenerationKey(
      Long userId,
      int chapter,
      int stage,
      String kcCode,
      int difficulty,
      String preferredKind,
      String arenaLevel,
      String expeditionRunId,
      String expeditionNodeId) {}

  record CachedProblem(BossInternalProblem problem, Instant cachedAt) {}
}
