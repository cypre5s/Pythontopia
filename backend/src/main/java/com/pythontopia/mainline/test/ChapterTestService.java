package com.pythontopia.mainline.test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.mainline.ChapterCatalog;
import com.pythontopia.mainline.ChapterUnlockGuard;
import com.pythontopia.mainline.MainlineStageProgressRepository;
import com.pythontopia.mainline.MainlineStageRepository;
import com.pythontopia.mainline.domain.ChapterAssessmentKind;
import com.pythontopia.mainline.domain.ChapterTestRecord;
import com.pythontopia.mainline.domain.ChapterTestStatus;
import com.pythontopia.mainline.domain.MainlineStage;
import com.pythontopia.mainline.domain.MainlineStageProgress;
import com.pythontopia.mainline.dto.ChapterTestPaperDto;
import com.pythontopia.mainline.dto.ChapterTestResultDto;
import com.pythontopia.mainline.dto.ChapterTestSubmitRequest;
import com.pythontopia.mainline.dto.TestPrepareResponse;
import com.pythontopia.mainline.star.ChapterStarRepository;
import com.pythontopia.mainline.star.ChapterStarService;
import com.pythontopia.mainline.star.StarSource;
import com.pythontopia.mainline.test.ChapterTestJudge.JudgeReport;
import com.pythontopia.mainline.test.ChapterTestJudge.QuestionJudgement;
import com.pythontopia.mainline.test.question.QuestionItem;
import com.pythontopia.mainline.test.question.QuestionPaper;
import com.pythontopia.mainline.test.question.QuestionType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 章末大测试编排（r15 计划 §4 / §9）：prepare（触发异步预生成）/ start（去答案发卷 + 限时）/
 * submit（服务端判分 + 星级重算）/ latest。<b>考试期间零 LLM</b>：start/submit 仅读库 + 本地判分。
 */
@Service
public class ChapterTestService {

  private static final Logger log = LoggerFactory.getLogger(ChapterTestService.class);
  private static final int QUIZ_TIME_LIMIT_SEC = 3600;
  private static final int FINAL_TIME_LIMIT_SEC = 7200;

  private final ChapterTestRepository repository;
  private final ChapterTestPregenService pregenService;
  private final ChapterTestJudge judge;
  private final ChapterTestMapper mapper;
  private final ChapterStarService starService;
  private final ChapterStarRepository starRepository;
  private final MainlineStageRepository stageRepository;
  private final MainlineStageProgressRepository progressRepository;
  private final ObjectMapper objectMapper;
  private final ApplicationEventPublisher eventPublisher;

  public ChapterTestService(
      ChapterTestRepository repository,
      ChapterTestPregenService pregenService,
      ChapterTestJudge judge,
      ChapterTestMapper mapper,
      ChapterStarService starService,
      ChapterStarRepository starRepository,
      MainlineStageRepository stageRepository,
      MainlineStageProgressRepository progressRepository,
      ObjectMapper objectMapper,
      ApplicationEventPublisher eventPublisher) {
    this.repository = repository;
    this.pregenService = pregenService;
    this.judge = judge;
    this.mapper = mapper;
    this.starService = starService;
    this.starRepository = starRepository;
    this.stageRepository = stageRepository;
    this.progressRepository = progressRepository;
    this.objectMapper = objectMapper;
    this.eventPublisher = eventPublisher;
  }

  /**
   * 触发/复用预生成（幂等）。已在 GENERATING/READY/<b>未超时的</b> IN_PROGRESS 则直接返回当前状态不重复触发；
   * 否则新建 attempt 置 GENERATING 并投递异步生成。
   *
   * <p>已超时但仍挂在 IN_PROGRESS 的场次必须走重开：否则 prepare 回 IN_PROGRESS 让学生「继续考」，
   * 而 start 会把它判过期后抛「题目仍在生成，请稍后重试」，学生按提示等待却永远等不到。
   */
  public TestPrepareResponse prepare(long userId, int chapter) {
    return prepare(userId, chapter, ChapterAssessmentKind.FINAL);
  }

  public TestPrepareResponse prepareQuiz(long userId, int chapter) {
    return prepare(userId, chapter, ChapterAssessmentKind.QUIZ);
  }

  private TestPrepareResponse prepare(
      long userId, int chapter, ChapterAssessmentKind assessmentKind) {
    requireChapter(chapter);
    ChapterTestRecord latest =
        repository.findLatest(userId, chapter, assessmentKind).orElse(null);
    if (latest != null
        && (latest.status() == ChapterTestStatus.GENERATING
            || latest.status() == ChapterTestStatus.READY
            || (latest.status() == ChapterTestStatus.IN_PROGRESS && !isPastDeadline(latest)))) {
      return new TestPrepareResponse(latest.status().name());
    }
    if (latest != null && latest.status() == ChapterTestStatus.IN_PROGRESS) {
      repository.markStatus(latest.id(), ChapterTestStatus.EXPIRED); // 超时残留场次作废，允许重新准备
    }
    int attemptNo = repository.nextAttemptNo(userId, chapter, assessmentKind);
    int timeLimitSec =
        assessmentKind == ChapterAssessmentKind.QUIZ
            ? QUIZ_TIME_LIMIT_SEC
            : FINAL_TIME_LIMIT_SEC;
    long testId =
        repository.insertPending(userId, chapter, assessmentKind, attemptNo, timeLimitSec);
    repository.markStatus(testId, ChapterTestStatus.GENERATING);
    List<String> exclusions =
        assessmentKind == ChapterAssessmentKind.FINAL
            ? repository
                .findLatest(userId, chapter, ChapterAssessmentKind.QUIZ)
                .map(ChapterTestRecord::questions)
                .map(this::questionSignatures)
                .orElse(List.of())
            : List.of();
    pregenService.pregenerate(testId, userId, chapter, assessmentKind, exclusions);
    log.info(
        "章节测评预生成已触发 user={} ch={} kind={} testId={} attempt={}",
        userId,
        chapter,
        assessmentKind,
        testId,
        attemptNo);
    return new TestPrepareResponse(ChapterTestStatus.GENERATING.name());
  }

  /**
   * 开考：下发去答案题面 + deadline。一章同时至多一份 IN_PROGRESS（未过期则幂等复用同一份）。
   * 尚未 READY 时返回明确冲突；不使用旧题量兜底，以免题量缩水。
   */
  public ChapterTestPaperDto start(long userId, int chapter) {
    return start(userId, chapter, ChapterAssessmentKind.FINAL);
  }

  public ChapterTestPaperDto startQuiz(long userId, int chapter) {
    return start(userId, chapter, ChapterAssessmentKind.QUIZ);
  }

  private ChapterTestPaperDto start(
      long userId, int chapter, ChapterAssessmentKind assessmentKind) {
    requireChapter(chapter);
    ChapterUnlockGuard.requireUnlocked(starRepository, userId, chapter);
    requireFinalStagesCompleted(userId, chapter, assessmentKind);
    ChapterTestRecord latest =
        repository.findLatest(userId, chapter, assessmentKind).orElse(null);

    // 1) 已有进行中：未过期则原样复用（幂等再进入）；已过期则置 EXPIRED 走重开。
    if (latest != null && latest.status() == ChapterTestStatus.IN_PROGRESS) {
      if (!isPastDeadline(latest)) {
        return mapper.toPaper(latest, parsePaper(latest));
      }
      repository.markStatus(latest.id(), ChapterTestStatus.EXPIRED);
      latest = repository.findLatest(userId, chapter, assessmentKind).orElse(latest);
    }

    if (latest == null || latest.status() != ChapterTestStatus.READY) {
      // EXPIRED 单独成文案：此时「等一等」没有意义，必须重新 prepare 才会有新卷。
      if (latest != null && latest.status() == ChapterTestStatus.EXPIRED) {
        throw new BusinessException(
            HttpStatus.CONFLICT, "CHAPTER_ASSESSMENT_EXPIRED", "本场已超时，请重新准备");
      }
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "CHAPTER_ASSESSMENT_NOT_READY",
          latest != null && latest.status() == ChapterTestStatus.FAILED
              ? "题目生成失败，请重新准备"
              : "题目仍在生成，请稍后重试");
    }
    long testId = latest.id();

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
  @Transactional
  public ChapterTestResultDto submit(
      long userId, int chapter, long testId, ChapterTestSubmitRequest request) {
    return submit(
        userId, chapter, testId, request, ChapterAssessmentKind.FINAL);
  }

  @Transactional
  public ChapterTestResultDto submitQuiz(
      long userId, int chapter, long testId, ChapterTestSubmitRequest request) {
    return submit(
        userId, chapter, testId, request, ChapterAssessmentKind.QUIZ);
  }

  private ChapterTestResultDto submit(
      long userId,
      int chapter,
      long testId,
      ChapterTestSubmitRequest request,
      ChapterAssessmentKind assessmentKind) {
    requireChapter(chapter);
    ChapterTestRecord record =
        repository
            .findOwnedForUpdate(userId, testId)
            .filter(r -> r.chapter() == chapter && r.assessmentKind() == assessmentKind)
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

    publishCodingAssessmentEvents(userId, chapter, testId, assessmentKind, paper, report);

    int stars =
        assessmentKind == ChapterAssessmentKind.FINAL
            ? starService.recalc(userId, chapter, StarSource.TEST).stars()
            : currentStars(userId, chapter);
    ChapterTestRecord submitted = repository.findById(testId).orElseThrow();
    log.info(
        "章测提交 user={} ch={} testId={} score={}/{} stars={}",
        userId, chapter, testId, report.score(), report.maxScore(), stars);
    return mapper.toResult(submitted, report, stars, false);
  }

  private void publishCodingAssessmentEvents(
      long userId,
      int chapter,
      long testId,
      ChapterAssessmentKind assessmentKind,
      QuestionPaper paper,
      JudgeReport report) {
    Map<String, QuestionJudgement> judgementsByQid =
        report.items().stream()
            .collect(
                Collectors.toMap(
                    QuestionJudgement::qid, Function.identity(), (first, duplicate) -> first));
    Map<String, List<QuestionItem>> codingItemsByKc =
        paper.items().stream()
            .filter(item -> item.type() == QuestionType.CODING)
            .filter(item -> item.kcCode() != null && !item.kcCode().isBlank())
            .collect(Collectors.groupingBy(QuestionItem::kcCode));

    codingItemsByKc.forEach(
        (kcCode, items) -> {
          int correct = (int) items.stream()
              .map(item -> judgementsByQid.get(item.qid()))
              .filter(judgement -> judgement != null && judgement.correct())
              .count();
          eventPublisher.publishEvent(
              new LearningEvent.ChapterAssessmentScored(
                  userId, testId, chapter, assessmentKind.name(), kcCode, correct, items.size()));
        });
  }

  /** 该章最近一场结果（无则 null）。仅终态回填逐题明细 + 答案。 */
  public ChapterTestResultDto latest(long userId, int chapter) {
    return latest(userId, chapter, ChapterAssessmentKind.FINAL);
  }

  public ChapterTestResultDto latestQuiz(long userId, int chapter) {
    return latest(userId, chapter, ChapterAssessmentKind.QUIZ);
  }

  private ChapterTestResultDto latest(
      long userId, int chapter, ChapterAssessmentKind assessmentKind) {
    requireChapter(chapter);
    return repository
        .findLatest(userId, chapter, assessmentKind)
        .map(record -> mapper.fromStored(record, currentStars(userId, chapter)))
        .orElse(null);
  }

  // --- 内部 -----------------------------------------------------------------

  private boolean isPastDeadline(ChapterTestRecord record) {
    return record.deadlineAt() != null && OffsetDateTime.now().isAfter(record.deadlineAt());
  }

  private int currentStars(long userId, int chapter) {
    return starRepository.find(userId, chapter).map(s -> s.stars()).orElse(0);
  }

  private void requireFinalStagesCompleted(
      long userId, int chapter, ChapterAssessmentKind assessmentKind) {
    if (assessmentKind != ChapterAssessmentKind.FINAL) {
      return;
    }
    List<MainlineStage> stages = stageRepository.findByChapter(chapter);
    if (stages.isEmpty()) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "CHAPTER_FINAL_LOCKED", "本章学习小节尚未初始化，暂不能参加章末大测试");
    }
    Set<Long> completedStageIds =
        progressRepository.findByUser(userId).stream()
            .filter(progress -> progress.completedAt() != null)
            .map(MainlineStageProgress::stageId)
            .collect(Collectors.toSet());
    boolean allCompleted = stages.stream().allMatch(stage -> completedStageIds.contains(stage.id()));
    if (!allCompleted) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "CHAPTER_FINAL_LOCKED",
          "先完成本章所有小节的看、学、练，再参加章末大测试");
    }
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

  List<String> questionSignatures(JsonNode questions) {
    if (questions == null || !questions.path("items").isArray()) {
      return List.of();
    }
    List<String> signatures = new java.util.ArrayList<>();
    for (JsonNode item : questions.path("items")) {
      String raw =
          "CODING".equals(item.path("type").asText())
              ? item.path("title").asText("") + " " + item.path("statement_markdown").asText("")
              : item.path("stem").asText("");
      if (!raw.isBlank()) {
        signatures.add(raw);
      }
    }
    return List.copyOf(signatures);
  }

  private void requireChapter(int chapter) {
    if (!ChapterCatalog.isValidChapter(chapter)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "MAINLINE_CHAPTER_INVALID", "chapter 必须在 1..7 之间");
    }
  }
}
