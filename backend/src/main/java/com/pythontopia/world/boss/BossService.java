package com.pythontopia.world.boss;

import com.pythontopia.coursepack.domain.CourseKc;
import com.pythontopia.coursepack.repository.CoursePackRepository;
import com.pythontopia.learner.mastery.MasteryRepository;
import com.pythontopia.learner.mastery.MasterySnapshot;
import com.pythontopia.world.boss.dto.BossAttemptRequest;
import com.pythontopia.world.boss.dto.BossAttemptResultDto;
import com.pythontopia.world.boss.dto.BossEvaluateResultDto;
import com.pythontopia.world.boss.dto.BossInternalProblem;
import com.pythontopia.world.boss.dto.BossProblemDto;
import com.pythontopia.world.boss.dto.BossProblemRequest;
import com.pythontopia.world.boss.dto.BossTestCasePayload;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
  private final ConcurrentMap<String, CachedProblem> cache = new ConcurrentHashMap<>();

  public BossService(
      BossGraphClient graphClient,
      MasteryRepository masteryRepository,
      CoursePackRepository coursePackRepository,
      EndlessPregenService pregenService) {
    this.graphClient = graphClient;
    this.masteryRepository = masteryRepository;
    this.coursePackRepository = coursePackRepository;
    this.pregenService = pregenService;
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

  /** 章号 1..7 → 默认 KC 起始码（与 MainQuest data 表对齐）。 */
  String resolveKc(int chapter, String override) {
    if (override != null && !override.isBlank()) {
      return override;
    }
    return switch (chapter) {
      case 1 -> "PY43-KC-001";
      case 2 -> "PY43-KC-006";
      case 3 -> "PY43-KC-016";
      case 4 -> "PY43-KC-026";
      case 5 -> "PY43-KC-036";
      case 6 -> "PY43-KC-046";
      case 7 -> "PY43-KC-056";
      default -> "PY43-KC-001";
    };
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
