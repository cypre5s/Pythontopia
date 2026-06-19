package com.pythontopia.world.boss;

import com.pythontopia.coursepack.domain.CourseKc;
import com.pythontopia.coursepack.repository.CoursePackRepository;
import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.learner.mastery.MasteryRepository;
import com.pythontopia.learner.mastery.MasterySnapshot;
import com.pythontopia.mainline.ChapterCatalog;
import com.pythontopia.world.boss.dto.BossAttemptRequest;
import com.pythontopia.world.boss.dto.BossAttemptResultDto;
import com.pythontopia.world.boss.dto.BossEvaluateResultDto;
import com.pythontopia.world.boss.dto.BossInternalProblem;
import com.pythontopia.world.boss.dto.BossProblemDto;
import com.pythontopia.world.boss.dto.BossProblemRequest;
import com.pythontopia.world.boss.dto.BossTestCasePayload;
import com.pythontopia.world.boss.dto.FrontierSelectRequest;
import com.pythontopia.world.boss.dto.FrontierSelectResultDto;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  private final BossGraphClient graphClient;
  private final MasteryRepository masteryRepository;
  private final CoursePackRepository coursePackRepository;
  private final EndlessPregenService pregenService;
  private final ApplicationEventPublisher eventPublisher;
  private final ConcurrentMap<String, CachedProblem> cache = new ConcurrentHashMap<>();

  public BossService(
      BossGraphClient graphClient,
      MasteryRepository masteryRepository,
      CoursePackRepository coursePackRepository,
      EndlessPregenService pregenService,
      ApplicationEventPublisher eventPublisher) {
    this.graphClient = graphClient;
    this.masteryRepository = masteryRepository;
    this.coursePackRepository = coursePackRepository;
    this.pregenService = pregenService;
    this.eventPublisher = eventPublisher;
  }

  /**
   * 无尽远征知识前沿选择（下沉自前端）：从权威掌握度 + 画像 + 近期 KC，调 graph 选择器返回候选。
   *
   * <p>graph 不可达时返回空 picks（前端回退本地 useKcFrontier，绝不阻塞玩家）。
   */
  public FrontierSelectResultDto selectFrontier(Long userId, FrontierSelectRequest request) {
    Map<String, Double> mastery = new HashMap<>();
    // 间隔重复持久化：复用 kc_mastery.last_event_at 作为「每 KC 最近练习时间」，传给 graph 做真·间隔
    // 重复（久未练上浮），而非仅靠前端 recentKcs。ISO_OFFSET_DATE_TIME 保证 "...:00Z" 完整秒位。
    Map<String, String> lastPracticed = new HashMap<>();
    if (userId != null) {
      for (MasterySnapshot snapshot : masteryRepository.findAll(userId)) {
        if (snapshot.kcCode() == null) {
          continue;
        }
        if (snapshot.masteryScore() != null) {
          mastery.put(snapshot.kcCode(), snapshot.masteryScore().doubleValue());
        }
        if (snapshot.lastEventAt() != null) {
          lastPracticed.put(
              snapshot.kcCode(),
              snapshot.lastEventAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
      }
    }
    try {
      FrontierSelectResultDto result =
          graphClient.selectFrontier(
              mastery,
              request.profileSignals(),
              request.recentKcs(),
              request.focusChapter(),
              request.exclude(),
              request.count(),
              lastPracticed);
      return result == null ? FrontierSelectResultDto.empty() : result;
    } catch (RestClientException ex) {
      LOG.warn("select-frontier graph unavailable, returning empty (frontend falls back): {}", ex.getMessage());
      return FrontierSelectResultDto.empty();
    }
  }

  public BossProblemDto fetchProblem(int chapter, BossProblemRequest request, Long userId) {
    // preferredKc 优先级高于旧 kcCode（前端 ArenaPage 走 preferredKc）。
    String effectiveKc = request.preferredKc() != null ? request.preferredKc() : request.kcCode();
    String kc = resolveKc(chapter, effectiveKc);
    // 难度优先取前端显式请求；否则据该章 KC 的真实掌握度 + 置信度推断（学情驱动），而非仅按 stage。
    int difficulty =
        request.difficulty() != null
            ? request.difficulty()
            : computeTargetDifficulty(userId, kc, request.stage());

    // 1) 优先复用「已预生成未做」(READY) 的同维度题：命中即原子标 CONSUMED，省去现场等 LLM。
    //    这条同时实现「学生中途退出，下次登入复用已生成未做的题」——上次会话预取留下的 READY 行。
    Optional<BossInternalProblem> reused =
        pregenService.claimReady(userId, chapter, kc, request.preferredKind(), request.arenaLevel());
    if (reused.isPresent()) {
      BossInternalProblem p = reused.get();
      // 回灌内存缓存（用题目原 problemId 作键），供随后的 evaluateAttempt 直接取 hidden testCases。
      cache.put(p.problemId(), new CachedProblem(p, Instant.now()));
      evictExpired();
      LOG.info("boss reuse pregenerated problemId={} user={} ch={}", p.problemId(), userId, chapter);
      // 展示时套用「本次请求」的 stage + 本次推断难度：预取时的 stage/难度不串到当前上下文
      // （也避免共享端点下复用题把异常 stage 显示给主线 boss）。
      return new BossProblemDto(
          p.problemId(),
          chapter,
          request.stage(),
          difficulty,
          p.kcCode(),
          p.statement(),
          p.hints(),
          p.sampleInput(),
          p.sampleOutput(),
          p.totalTestCases(),
          p.reviewed(),
          p.aiGenerated());
    }

    // 2) 未命中 → 现场走 graph 出题（失败回退 golden）。
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
              request.profileSignals());
      if (fromGraph != null && fromGraph.problemId() != null) {
        cache.put(fromGraph.problemId(), new CachedProblem(fromGraph, Instant.now()));
        // 现场题也落库 (CONSUMED)，供内存缓存过期 / 进程重启后判题兜底取回 testCases（best-effort）。
        pregenService.persistConsumed(
            userId, chapter, kc, request.preferredKind(), request.arenaLevel(), difficulty,
            fromGraph);
        evictExpired();
        return fromGraph.toPublic();
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
   * 立即返回，真正生成在 {@link EndlessPregenService#pregenerate} 的 {@code @Async} 线程进行；
   * 幂等 + best-effort。前端在 phase='playing' 后 fire-and-forget 调用，玩家做题时后台备好下一题。
   */
  public void prefetch(int chapter, BossProblemRequest request, Long userId) {
    String effectiveKc = request.preferredKc() != null ? request.preferredKc() : request.kcCode();
    String kc = resolveKc(chapter, effectiveKc);
    int difficulty =
        request.difficulty() != null
            ? request.difficulty()
            : computeTargetDifficulty(userId, kc, request.stage());
    pregenService.pregenerate(
        userId,
        chapter,
        request.stage(),
        kc,
        difficulty,
        request.preferredKc(),
        request.preferredKind(),
        request.arenaLevel(),
        request.profileSignals());
  }

  public BossAttemptResultDto evaluateAttempt(BossAttemptRequest request, Long userId) {
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
      String feedback =
          graphResult.passed()
              ? "通过 ✅ 服务端沙箱已确认。"
              : graphResult.runtimeError() != null
                  ? "代码运行错误：" + graphResult.runtimeError()
                  : "未通过 ❌ 部分用例失败，请参考下方 hint。";
      // 闭环关键：服务端沙箱真实判完后（且仅此路径）才发学习事件，与 SubmissionService 同一套事件驱动
      // KC 掌握度（测）+ 8 维画像被动修订。缓存过期 / 缺 testCases / graph 不可用等「未判定」路径不发，
      // 避免把非判题结果污染进掌握度。单次 attempt 仅发一次，天然幂等。
      publishPracticeOutcome(userId, problem, graphResult);
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
   * 取判题所需完整题（含 hidden testCases）：先查进程内缓存（TTL 内有效）；过期 / 未命中再回退
   * {@code pregenerated_problem} 凭 user + problemId 取最新一条 payload，命中后回灌缓存。
   * 二者皆空返回 {@code null}（题目已无法判，调用方给 Pyodide 兜底提示）。
   */
  private BossInternalProblem resolveProblemForEval(String problemId, Long userId) {
    CachedProblem cached = cache.get(problemId);
    if (cached != null) {
      if (Duration.between(cached.cachedAt(), Instant.now()).compareTo(CACHE_TTL) <= 0) {
        return cached.problem();
      }
      cache.remove(problemId); // 过期，落 DB 再取
    }
    BossInternalProblem fromDb = pregenService.findByProblemId(userId, problemId).orElse(null);
    if (fromDb != null) {
      cache.put(problemId, new CachedProblem(fromDb, Instant.now())); // 回灌缓存避免重复查库
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

  /**
   * Boss 目标难度 1..5：据锚定 KC 所在章节的真实掌握度 + 置信度推断（学情驱动出题）。无任何学情
   * （新生 / 该章无掌握记录 / 课程包读失败）时退化为按 stage 的旧逻辑，保证演示链路不断。
   */
  int computeTargetDifficulty(Long userId, String anchorKc, int stage) {
    int stageFallback = Math.min(5, Math.max(1, stage));
    if (userId == null || anchorKc == null) {
      return stageFallback;
    }
    try {
      List<CourseKc> kcs = coursePackRepository.findKcs(DEFAULT_PACK_ID, null);
      Long chapterId = null;
      for (CourseKc kc : kcs) {
        if (anchorKc.equals(kc.kcCode())) {
          chapterId = kc.chapterId();
          break;
        }
      }
      if (chapterId == null) {
        return stageFallback;
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
        return stageFallback;
      }
      return bossDifficulty(scoreSum / n, confSum / n);
    } catch (RuntimeException ex) {
      LOG.debug("boss target difficulty degraded for user {}: {}", userId, ex.getMessage());
      return stageFallback;
    }
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
    if (override != null && !override.isBlank()) {
      return override;
    }
    int safeChapter = ChapterCatalog.isValidChapter(chapter) ? chapter : 1;
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
  record CachedProblem(BossInternalProblem problem, Instant cachedAt) {}
}
