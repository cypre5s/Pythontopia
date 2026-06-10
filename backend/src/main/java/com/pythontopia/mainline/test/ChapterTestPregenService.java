package com.pythontopia.mainline.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.mainline.ChapterCatalog;
import com.pythontopia.mainline.MainlineProperties;
import com.pythontopia.mainline.domain.ChapterTestStatus;
import com.pythontopia.mainline.test.ChapterTestGraphClient.GenerateRequest;
import com.pythontopia.mainline.test.ChapterTestGraphClient.GeneratedPaper;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 章末大测试<b>异步预生成</b>（r15 计划 §4.1 / §11.1-2，高并发关键路径）。
 *
 * <ul>
 *   <li>状态机：PENDING/FAILED → {@code GENERATING} →(graph ok)→ {@code READY}；失败重试 ≤
 *       {@code testGenerateMaxRetries}（默认 2），全败 → 落 {@code FAILED} 后走 golden 兜底再 {@code READY}。
 *   <li>节流：信号量 {@code pythontopia.mainline.pregen-concurrency}（默认 2）限制 backend→graph 并发；
 *       graph 单 worker 不变，靠此处削峰（10 学生同时 prepare 不超并发）。
 *   <li>best-effort：本方法<b>绝不</b>向调用线程抛异常（{@code @Async} 旁路），考试 start 永不等 LLM。
 * </ul>
 */
@Service
public class ChapterTestPregenService {

  private static final Logger log = LoggerFactory.getLogger(ChapterTestPregenService.class);

  // §3 默认构成：选择×5 + 判断×3 + 填空×3 + 编程×1 = 100 分。
  private static final ChapterTestGraphClient.GenerateRequest.Counts DEFAULT_COUNTS =
      new ChapterTestGraphClient.GenerateRequest.Counts(5, 3, 3, 1);

  private final ChapterTestRepository repository;
  private final ChapterTestGraphClient graphClient;
  private final GoldenPaperProvider goldenProvider;
  private final ObjectMapper objectMapper;
  private final MainlineProperties properties;
  private final Semaphore pregenSemaphore;

  public ChapterTestPregenService(
      ChapterTestRepository repository,
      ChapterTestGraphClient graphClient,
      GoldenPaperProvider goldenProvider,
      ObjectMapper objectMapper,
      MainlineProperties properties) {
    this.repository = repository;
    this.graphClient = graphClient;
    this.goldenProvider = goldenProvider;
    this.objectMapper = objectMapper;
    this.properties = properties;
    this.pregenSemaphore = new Semaphore(properties.pregenConcurrency());
  }

  /**
   * 异步生成题面。best-effort：成功置 READY，全败置 FAILED 后 golden 兜底再 READY；任何情况不抛给调用线程。
   */
  @Async
  public void pregenerate(long testId, long userId, int chapter) {
    boolean acquired = false;
    try {
      pregenSemaphore.acquire();
      acquired = true;
      generateWithRetry(testId, userId, chapter);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      log.warn("pregenerate 被中断 testId={} ch={}", testId, chapter);
      applyGoldenFallback(testId, chapter, "interrupted");
    } catch (RuntimeException ex) {
      log.warn("pregenerate 异常 testId={} ch={}: {}", testId, chapter, ex.getMessage());
      applyGoldenFallback(testId, chapter, ex.getMessage());
    } finally {
      if (acquired) {
        pregenSemaphore.release();
      }
    }
  }

  private void generateWithRetry(long testId, long userId, int chapter) {
    int maxAttempts = 1 + properties.testGenerateMaxRetries();
    GenerateRequest request =
        new GenerateRequest(
            userId,
            chapter,
            ChapterCatalog.kcCodes(chapter),
            // TODO(integration/B1)：接入真实 8 维画像摘要 + errorbook 高频错因 brief（§10 个性化）。
            //   A1 暂传空 brief（不伪造画像内容）；graph 端需对空 brief 兜底出题。
            objectMapper.createObjectNode(),
            objectMapper.createObjectNode(),
            DEFAULT_COUNTS);
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        GeneratedPaper paper = graphClient.generate(request);
        repository.markReady(testId, paper.questions(), paper.generatorMeta(), null);
        log.info("章测预生成成功 testId={} ch={} attempt={}", testId, chapter, attempt);
        return;
      } catch (ChapterTestGenerationException ex) {
        log.warn(
            "章测预生成失败 testId={} ch={} attempt={}/{}: {}",
            testId, chapter, attempt, maxAttempts, ex.getMessage());
      }
    }
    // 重试耗尽：先记 FAILED（审计），再 golden 兜底转 READY（考试可进行，零 LLM）。
    repository.markStatus(testId, ChapterTestStatus.FAILED);
    applyGoldenFallback(testId, chapter, "graph 生成重试耗尽");
  }

  /** golden 兜底：写占位题组 + 审计（标注 fallback 原因），状态置 READY。 */
  private void applyGoldenFallback(long testId, int chapter, String reason) {
    try {
      var meta = goldenProvider.meta(chapter);
      if (meta.isObject()) {
        ((com.fasterxml.jackson.databind.node.ObjectNode) meta).put("fallback_reason", reason);
      }
      repository.markReady(testId, goldenProvider.loadQuestions(chapter), meta, null);
      log.info("章测 golden 兜底就绪 testId={} ch={} reason={}", testId, chapter, reason);
    } catch (RuntimeException ex) {
      // 连兜底都失败属配置错误：保持 FAILED，start 时再尝试 golden（仍失败则明确报错，不静默）。
      repository.markStatus(testId, ChapterTestStatus.FAILED);
      log.error("章测 golden 兜底失败 testId={} ch={}: {}", testId, chapter, ex.getMessage());
    }
  }
}
