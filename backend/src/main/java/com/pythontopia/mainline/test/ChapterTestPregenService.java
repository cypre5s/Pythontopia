package com.pythontopia.mainline.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.mainline.ChapterCatalog;
import com.pythontopia.mainline.MainlineProperties;
import com.pythontopia.mainline.domain.ChapterAssessmentKind;
import com.pythontopia.mainline.domain.ChapterTestStatus;
import com.pythontopia.mainline.test.ChapterProblemBankRepository.PreferredCodingQuestion;
import com.pythontopia.mainline.test.ChapterTestGraphClient.GenerateRequest;
import com.pythontopia.mainline.test.ChapterTestGraphClient.GeneratedPaper;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 章节小测/章末大测试<b>异步预生成</b>（r15 计划 §4.1 / §11.1-2，高并发关键路径）。
 *
 * <ul>
 *   <li>状态机：PENDING/FAILED → {@code GENERATING} →(graph ok)→ {@code READY}；失败重试 ≤
 *       {@code testGenerateMaxRetries}（默认 2），全败 → {@code FAILED}。
 *   <li>节流：信号量 {@code pythontopia.mainline.pregen-concurrency}（默认 2）限制 backend→graph 并发；
 *       graph 单 worker 不变，靠此处削峰（10 学生同时 prepare 不超并发）。
 *   <li>best-effort：本方法<b>绝不</b>向调用线程抛异常（{@code @Async} 旁路），考试 start 永不等 LLM。
 * </ul>
 */
@Service
public class ChapterTestPregenService {

  private static final Logger log = LoggerFactory.getLogger(ChapterTestPregenService.class);

  static final ChapterTestGraphClient.GenerateRequest.Counts QUIZ_COUNTS =
      new ChapterTestGraphClient.GenerateRequest.Counts(10, 10, 10, 3);
  static final ChapterTestGraphClient.GenerateRequest.Counts FINAL_COUNTS =
      new ChapterTestGraphClient.GenerateRequest.Counts(30, 30, 30, 9);

  private final ChapterTestRepository repository;
  private final ChapterTestGraphClient graphClient;
  private final ChapterProblemBankRepository problemBankRepository;
  private final ObjectMapper objectMapper;
  private final MainlineProperties properties;
  private final Semaphore pregenSemaphore;

  public ChapterTestPregenService(
      ChapterTestRepository repository,
      ChapterTestGraphClient graphClient,
      ChapterProblemBankRepository problemBankRepository,
      ObjectMapper objectMapper,
      MainlineProperties properties) {
    this.repository = repository;
    this.graphClient = graphClient;
    this.problemBankRepository = problemBankRepository;
    this.objectMapper = objectMapper;
    this.properties = properties;
    this.pregenSemaphore = new Semaphore(properties.pregenConcurrency());
  }

  /** 异步生成题面。best-effort：成功置 READY，全败置 FAILED；任何情况不抛给调用线程。 */
  @Async
  public void pregenerate(long testId, long userId, int chapter) {
    pregenerate(testId, userId, chapter, ChapterAssessmentKind.FINAL, List.of());
  }

  @Async
  public void pregenerate(
      long testId,
      long userId,
      int chapter,
      ChapterAssessmentKind assessmentKind,
      List<String> excludedQuestions) {
    boolean acquired = false;
    try {
      pregenSemaphore.acquire();
      acquired = true;
      generateWithRetry(testId, userId, chapter, assessmentKind, excludedQuestions);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      log.warn("pregenerate 被中断 testId={} ch={}", testId, chapter);
      repository.markStatus(testId, ChapterTestStatus.FAILED);
    } catch (RuntimeException ex) {
      log.warn("pregenerate 异常 testId={} ch={}: {}", testId, chapter, ex.getMessage());
      repository.markStatus(testId, ChapterTestStatus.FAILED);
    } finally {
      if (acquired) {
        pregenSemaphore.release();
      }
    }
  }

  private void generateWithRetry(
      long testId,
      long userId,
      int chapter,
      ChapterAssessmentKind assessmentKind,
      List<String> excludedQuestions) {
    int maxAttempts = 1 + properties.testGenerateMaxRetries();
    GenerateRequest.Counts counts =
        assessmentKind == ChapterAssessmentKind.QUIZ ? QUIZ_COUNTS : FINAL_COUNTS;
    List<PreferredCodingQuestion> preferredCodingQuestions =
        problemBankRepository.findByKcCodes(ChapterCatalog.kcCodes(chapter));
    GenerateRequest request =
        new GenerateRequest(
            userId,
            chapter,
            ChapterCatalog.kcCodes(chapter),
            // TODO(integration/B1)：接入真实 8 维画像摘要 + errorbook 高频错因 brief（§10 个性化）。
            //   A1 暂传空 brief（不伪造画像内容）；graph 端需对空 brief 兜底出题。
            objectMapper.createObjectNode(),
            objectMapper.createObjectNode(),
            counts,
            excludedQuestions == null ? List.of() : List.copyOf(excludedQuestions),
            preferredCodingQuestions);
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        GeneratedPaper paper = graphClient.generate(request);
        validatePaper(paper.questions(), counts, request.excludedQuestions());
        repository.markReady(
            testId, paper.questions(), paper.generatorMeta(), paper.profileSnapshot());
        log.info(
            "章节测评预生成成功 testId={} ch={} kind={} attempt={}",
            testId,
            chapter,
            assessmentKind,
            attempt);
        return;
      } catch (ChapterTestGenerationException ex) {
        log.warn(
            "章测预生成失败 testId={} ch={} attempt={}/{}: {}",
            testId, chapter, attempt, maxAttempts, ex.getMessage());
      }
    }
    repository.markStatus(testId, ChapterTestStatus.FAILED);
  }

  private void validatePaper(
      JsonNode questions, GenerateRequest.Counts expected, List<String> excludedQuestions) {
    if (questions == null || !questions.path("items").isArray()) {
      throw new ChapterTestGenerationException("生成题面缺少 items");
    }
    Map<String, Integer> actual = new HashMap<>();
    for (JsonNode item : questions.path("items")) {
      actual.merge(item.path("type").asText(""), 1, Integer::sum);
    }
    if (actual.getOrDefault("CHOICE", 0) != expected.choice()
        || actual.getOrDefault("JUDGE", 0) != expected.judge()
        || actual.getOrDefault("FILL", 0) != expected.fill()
        || actual.getOrDefault("CODING", 0) != expected.coding()) {
      throw new ChapterTestGenerationException("生成题量不符合约定: " + actual);
    }
    var excluded =
        (excludedQuestions == null ? List.<String>of() : excludedQuestions).stream()
            .map(ChapterTestPregenService::signature)
            .filter(text -> !text.isBlank())
            .collect(java.util.stream.Collectors.toSet());
    for (JsonNode item : questions.path("items")) {
      String raw =
          "CODING".equals(item.path("type").asText())
              ? item.path("title").asText("") + " " + item.path("statement_markdown").asText("")
              : item.path("stem").asText("");
      if (excluded.contains(signature(raw))) {
        throw new ChapterTestGenerationException(
            "生成题目与章节小测重复: " + item.path("qid").asText(""));
      }
    }
  }

  static String signature(String text) {
    return text == null
        ? ""
        : text.replaceAll("[\\p{P}\\p{S}\\s_]+", "").toLowerCase(Locale.ROOT);
  }
}
