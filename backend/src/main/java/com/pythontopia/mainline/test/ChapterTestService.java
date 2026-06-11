package com.pythontopia.mainline.test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.mainline.ChapterCatalog;
import com.pythontopia.mainline.domain.ChapterTestRecord;
import com.pythontopia.mainline.domain.ChapterTestStatus;
import com.pythontopia.mainline.dto.ChapterTestPaperDto;
import com.pythontopia.mainline.dto.ChapterTestResultDto;
import com.pythontopia.mainline.dto.ChapterTestSubmitRequest;
import com.pythontopia.mainline.dto.TestPrepareResponse;
import com.pythontopia.mainline.star.ChapterStarRepository;
import com.pythontopia.mainline.star.ChapterStarService;
import com.pythontopia.mainline.star.StarSource;
import com.pythontopia.mainline.test.ChapterTestJudge.JudgeReport;
import com.pythontopia.mainline.test.question.QuestionPaper;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 章末大测试编排（r15 计划 §4 / §9）：prepare（触发异步预生成）/ start（去答案发卷 + 限时）/
 * submit（服务端判分 + 星级重算）/ latest。<b>考试期间零 LLM</b>：start/submit 仅读库 + 本地判分。
 */
@Service
public class ChapterTestService {

  private static final Logger log = LoggerFactory.getLogger(ChapterTestService.class);
  private static final int DEFAULT_TIME_LIMIT_SEC = 1800; // 30 分钟（§3）

  private final ChapterTestRepository repository;
  private final ChapterTestPregenService pregenService;
  private final GoldenPaperProvider goldenProvider;
  private final ChapterTestJudge judge;
  private final ChapterTestMapper mapper;
  private final ChapterStarService starService;
  private final ChapterStarRepository starRepository;
  private final ObjectMapper objectMapper;

  public ChapterTestService(
      ChapterTestRepository repository,
      ChapterTestPregenService pregenService,
      GoldenPaperProvider goldenProvider,
      ChapterTestJudge judge,
      ChapterTestMapper mapper,
      ChapterStarService starService,
      ChapterStarRepository starRepository,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.pregenService = pregenService;
    this.goldenProvider = goldenProvider;
    this.judge = judge;
    this.mapper = mapper;
    this.starService = starService;
    this.starRepository = starRepository;
    this.objectMapper = objectMapper;
  }

  /**
   * 触发/复用预生成（幂等）。已在 GENERATING/READY/IN_PROGRESS 则直接返回当前状态不重复触发；
   * 否则新建 attempt 置 GENERATING 并投递异步生成。
   */
  public TestPrepareResponse prepare(long userId, int chapter) {
    requireChapter(chapter);
    ChapterTestRecord latest = repository.findLatest(userId, chapter).orElse(null);
    if (latest != null
        && (latest.status() == ChapterTestStatus.GENERATING
            || latest.status() == ChapterTestStatus.READY
            || latest.status() == ChapterTestStatus.IN_PROGRESS)) {
      return new TestPrepareResponse(latest.status().name());
    }
    int attemptNo = repository.nextAttemptNo(userId, chapter);
    long testId = repository.insertPending(userId, chapter, attemptNo, DEFAULT_TIME_LIMIT_SEC);
    repository.markStatus(testId, ChapterTestStatus.GENERATING);
    pregenService.pregenerate(testId, userId, chapter); // @Async：考试 start 永不等 LLM
    log.info("章测预生成已触发 user={} ch={} testId={} attempt={}", userId, chapter, testId, attemptNo);
    return new TestPrepareResponse(ChapterTestStatus.GENERATING.name());
  }

  /**
   * 开考：下发去答案题面 + deadline。一章同时至多一份 IN_PROGRESS（未过期则幂等复用同一份）。
   * 若尚未 READY，直接 golden 兜底新建一份并开考（零 LLM，§4.1.3）。
   */
  public ChapterTestPaperDto start(long userId, int chapter) {
    requireChapter(chapter);
    ChapterTestRecord latest = repository.findLatest(userId, chapter).orElse(null);

    // 1) 已有进行中：未过期则原样复用（幂等再进入）；已过期则置 EXPIRED 走重开。
    if (latest != null && latest.status() == ChapterTestStatus.IN_PROGRESS) {
      if (!isPastDeadline(latest)) {
        return mapper.toPaper(latest, parsePaper(latest));
      }
      repository.markStatus(latest.id(), ChapterTestStatus.EXPIRED);
      latest = repository.findLatest(userId, chapter).orElse(latest);
    }

    // 2) 选定一份 READY 题面：已 READY 直接用；否则 golden 兜底新建。
    long testId;
    if (latest != null && latest.status() == ChapterTestStatus.READY) {
      testId = latest.id();
    } else {
      testId = seedGolden(userId, chapter);
    }

    // 3) 开考（READY→IN_PROGRESS）。
    OffsetDateTime startedAt = OffsetDateTime.now();
    ChapterTestRecord toStart = repository.findById(testId).orElseThrow();
    OffsetDateTime deadlineAt = startedAt.plusSeconds(toStart.timeLimitSec());
    boolean started = repository.startExam(testId, startedAt, deadlineAt);
    ChapterTestRecord active = repository.findById(testId).orElseThrow();
    if (!started && active.status() != ChapterTestStatus.IN_PROGRESS) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "CHAPTER_TEST_NOT_READY", "章测未就绪，请稍后重试开考");
    }
    return mapper.toPaper(active, parsePaper(active));
  }

  /**
   * 提交判分（服务端权威）。超时按 deadline 拒收并记 integrity.overtime；正常则判分入库 + 触发星级重算。
   * 幂等：对已 SUBMITTED 的同一 test 重复提交返回既有结果。
   */
  public ChapterTestResultDto submit(
      long userId, int chapter, long testId, ChapterTestSubmitRequest request) {
    requireChapter(chapter);
    ChapterTestRecord record =
        repository
            .findOwned(userId, testId)
            .filter(r -> r.chapter() == chapter)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "CHAPTER_TEST_NOT_FOUND", "章测不存在或无权访问"));

    if (record.status() == ChapterTestStatus.SUBMITTED) {
      return mapper.fromStored(record, currentStars(userId, chapter)); // 幂等返回既有结果
    }
    if (record.status() == ChapterTestStatus.EXPIRED) {
      throw new BusinessException(HttpStatus.CONFLICT, "CHAPTER_TEST_OVERTIME", "本场已超时，无法提交");
    }
    if (record.status() != ChapterTestStatus.IN_PROGRESS) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "CHAPTER_TEST_NOT_IN_PROGRESS", "请先开考再提交");
    }

    // 超时拒收：单行 UPDATE 落 EXPIRED + integrity.overtime（先提交持久化再抛，避免回滚丢审计）。
    if (isPastDeadline(record)) {
      repository.markExpiredOvertime(testId, integrityNode(request, true));
      throw new BusinessException(HttpStatus.CONFLICT, "CHAPTER_TEST_OVERTIME", "已超过考试时限，提交被拒收");
    }

    QuestionPaper paper = parsePaper(record);
    JudgeReport report = judge.judge(paper, request.answers());
    JsonNode detail = objectMapper.valueToTree(report);
    OffsetDateTime now = OffsetDateTime.now();
    boolean ok = repository.markSubmitted(testId, report.score(), detail, integrityNode(request, false), now);
    if (!ok) {
      // 并发下被他路转走：若已 SUBMITTED 幂等返回，否则报冲突。
      ChapterTestRecord reread = repository.findById(testId).orElseThrow();
      if (reread.status() == ChapterTestStatus.SUBMITTED) {
        return mapper.fromStored(reread, currentStars(userId, chapter));
      }
      throw new BusinessException(
          HttpStatus.CONFLICT, "CHAPTER_TEST_NOT_IN_PROGRESS", "提交状态冲突，请刷新重试");
    }

    int stars = starService.recalc(userId, chapter, StarSource.TEST).stars();
    ChapterTestRecord submitted = repository.findById(testId).orElseThrow();
    log.info(
        "章测提交 user={} ch={} testId={} score={}/{} stars={}",
        userId, chapter, testId, report.score(), report.maxScore(), stars);
    return mapper.toResult(submitted, report, stars, false);
  }

  /** 该章最近一场结果（无则 null）。仅终态回填逐题明细 + 答案。 */
  public ChapterTestResultDto latest(long userId, int chapter) {
    requireChapter(chapter);
    return repository
        .findLatest(userId, chapter)
        .map(record -> mapper.fromStored(record, currentStars(userId, chapter)))
        .orElse(null);
  }

  // --- 内部 -----------------------------------------------------------------

  private long seedGolden(long userId, int chapter) {
    int attemptNo = repository.nextAttemptNo(userId, chapter);
    long testId = repository.insertPending(userId, chapter, attemptNo, DEFAULT_TIME_LIMIT_SEC);
    repository.markReady(
        testId, goldenProvider.loadQuestions(chapter), goldenProvider.meta(chapter), null);
    log.info("章测 golden 兜底发卷 user={} ch={} testId={}", userId, chapter, testId);
    return testId;
  }

  private boolean isPastDeadline(ChapterTestRecord record) {
    return record.deadlineAt() != null && OffsetDateTime.now().isAfter(record.deadlineAt());
  }

  private int currentStars(long userId, int chapter) {
    return starRepository.find(userId, chapter).map(s -> s.stars()).orElse(0);
  }

  private QuestionPaper parsePaper(ChapterTestRecord record) {
    JsonNode questions = record.questions();
    if (questions == null || !questions.has("items")) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "CHAPTER_TEST_NOT_READY", "章测题面未就绪");
    }
    try {
      return objectMapper.treeToValue(questions, QuestionPaper.class);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("解析章测题面失败 testId=" + record.id(), ex);
    }
  }

  private JsonNode integrityNode(ChapterTestSubmitRequest request, boolean overtime) {
    ObjectNode node = objectMapper.createObjectNode();
    int paste = 0;
    int blur = 0;
    boolean clientOvertime = false;
    if (request != null && request.integrity() != null) {
      paste = request.integrity().pasteBlocked() == null ? 0 : request.integrity().pasteBlocked();
      blur = request.integrity().blurEvents() == null ? 0 : request.integrity().blurEvents();
      // R4：客户端自报「倒计时归零自动交卷」。按时送达的自动交卷服务端不超时，
      // 仅靠服务端拒收口径教师端看不到；两口径取并集供教师参考。
      clientOvertime = Boolean.TRUE.equals(request.integrity().overtime());
    }
    node.put("paste_blocked", paste);
    node.put("blur_events", blur);
    node.put("overtime", overtime || clientOvertime);
    return node;
  }

  private void requireChapter(int chapter) {
    if (!ChapterCatalog.isValidChapter(chapter)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "MAINLINE_CHAPTER_INVALID", "chapter 必须在 1..7 之间");
    }
  }
}
