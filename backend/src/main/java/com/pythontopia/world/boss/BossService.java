package com.pythontopia.world.boss;

import com.pythontopia.world.boss.dto.BossAttemptRequest;
import com.pythontopia.world.boss.dto.BossAttemptResultDto;
import com.pythontopia.world.boss.dto.BossEvaluateResultDto;
import com.pythontopia.world.boss.dto.BossInternalProblem;
import com.pythontopia.world.boss.dto.BossProblemDto;
import com.pythontopia.world.boss.dto.BossProblemRequest;
import com.pythontopia.world.boss.dto.BossTestCasePayload;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * R-13 §5 Phase 6：Boss 副本服务。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>把 chapter + stage 翻译成默认 KC 范围（与前端 {@code MAIN_QUEST_BOSSES} 表一致）；
 *   <li>调 {@link BossGraphClient} 拉 AI 出题；失败时退化到 golden test 题；
 *   <li>用 in-memory cache 持有完整 {@link BossInternalProblem}（含 hidden testCases），
 *       在 {@link #evaluateAttempt} 时取出 → 转发给 graph evaluate；
 *   <li>graph 评测失败时返回结构化 fallback，提示前端用 Pyodide 校验。
 * </ul>
 *
 * <p>缓存策略：默认 30 分钟 TTL（覆盖单局 4 阶段平均 8 分钟 + 重连缓冲），超时后
 * 玩家若仍未提交会拿到 {@code feedback="题目已过期"}；这个 case 极少（演示
 * 主流程提交在 30s 内）。
 */
@Service
public class BossService {

  private static final Logger LOG = LoggerFactory.getLogger(BossService.class);
  private static final Duration CACHE_TTL = Duration.ofMinutes(30);
  private static final int EVAL_TIMEOUT_SEC = 5;

  private final BossGraphClient graphClient;
  private final ConcurrentMap<String, CachedProblem> cache = new ConcurrentHashMap<>();

  public BossService(BossGraphClient graphClient) {
    this.graphClient = graphClient;
  }

  public BossProblemDto fetchProblem(int chapter, BossProblemRequest request, Long userId) {
    // R-13 §-1 续：preferredKc 优先级高于旧 kcCode（前端 ArenaPage 走 preferredKc）。
    String effectiveKc = request.preferredKc() != null ? request.preferredKc() : request.kcCode();
    String kc = resolveKc(chapter, effectiveKc);
    int difficulty =
        request.difficulty() == null ? Math.min(5, request.stage()) : request.difficulty();
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

  public BossAttemptResultDto evaluateAttempt(BossAttemptRequest request) {
    CachedProblem cached = cache.get(request.problemId());
    if (cached == null) {
      LOG.info("boss attempt problemId={} not in cache, returning Pyodide-only fallback",
          request.problemId());
      return new BossAttemptResultDto(
          request.problemId(),
          false,
          0,
          3,
          null,
          0,
          "题目缓存过期或服务重启；请用前端 Pyodide 校验后再次提交。");
    }
    if (Duration.between(cached.cachedAt(), Instant.now()).compareTo(CACHE_TTL) > 0) {
      cache.remove(request.problemId());
      return new BossAttemptResultDto(
          request.problemId(),
          false,
          0,
          cached.problem().totalTestCases(),
          null,
          0,
          "题目缓存已超 30 分钟，请重新生成题目。");
    }
    List<BossTestCasePayload> testCases = cached.problem().testCases();
    if (testCases.isEmpty()) {
      LOG.warn("cached problem {} has empty testCases (graph schema drift?)", request.problemId());
      return new BossAttemptResultDto(
          request.problemId(),
          false,
          0,
          cached.problem().totalTestCases(),
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
          cached.problem().totalTestCases(),
          null,
          0,
          "graph 评测服务不可用，请用前端 Pyodide 校验后再次提交。");
    }
  }

  /** Reactive cache eviction：用 size 上限 + TTL 双保险，避免无限增长。 */
  private void evictExpired() {
    if (cache.size() < 256) {
      return;
    }
    Instant now = Instant.now();
    cache.entrySet().removeIf(e -> Duration.between(e.getValue().cachedAt(), now).compareTo(CACHE_TTL) > 0);
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

  /** 分数公式：通过率 * 100；失败 0 ~ 50 之间按通过用例比例给。 */
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
