package com.pythontopia.bugmine.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pythontopia.bugmine.dto.BugMineCardDto;
import com.pythontopia.bugmine.dto.BugMineCardType;
import com.pythontopia.bugmine.dto.BugMineDepthStepDto;
import com.pythontopia.bugmine.dto.BugMineEchoCandidateDto;
import com.pythontopia.bugmine.dto.BugMineErrorBookItemDto;
import com.pythontopia.bugmine.dto.BugMineSessionDto;
import com.pythontopia.bugmine.dto.BugMineSessionStatus;
import com.pythontopia.bugmine.dto.BugMineVariantProblemDto;
import com.pythontopia.bugmine.graph.DebugAgentClient;
import com.pythontopia.bugmine.graph.DebugAgentRequest;
import com.pythontopia.bugmine.graph.DebugAgentResponse;
import com.pythontopia.bugmine.judge.CounterExampleSelector;
import com.pythontopia.bugmine.judge.PythonTraceCollector;
import com.pythontopia.bugmine.repository.BugMineRepository;
import com.pythontopia.bugmine.repository.BugMineRepository.SessionContextRow;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.learner.mastery.review.ReviewSignal;
import com.pythontopia.learner.mastery.review.ReviewStateService;
import com.pythontopia.submission.dto.PublicProblemDto;
import com.pythontopia.submission.dto.SubmissionCreateRequest;
import com.pythontopia.submission.dto.SubmissionDto;
import com.pythontopia.submission.dto.SubmissionSource;
import com.pythontopia.submission.dto.SubmissionStatus;
import com.pythontopia.submission.dto.Verdict;
import com.pythontopia.submission.repository.PracticeSubmissionRepository;
import com.pythontopia.submission.repository.PublicProblemRepository;
import com.pythontopia.submission.service.SubmissionService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bug 矿洞调试会话服务，维护 OPEN → RESOLVED/ABANDONED 状态机：
 * {@code open} 仅接受未 PASSED 的提交，收集反例与变量轨迹后由 DebugAgent 生成分层提示卡；
 * {@code retrySubmit} 受单会话重试上限约束，通过即转 RESOLVED 并发布 DebugResolved 事件；
 * {@code variantSubmit} 在 L5 对同 KC 变式题提交，通过后 mastery_confirmed 并 RESOLVED；
 * {@code close} 不可置回 OPEN，置为 ABANDONED 时发布 PracticeAbandoned 事件。
 */
@Service
public class BugMineService {

  private static final Logger log = LoggerFactory.getLogger(BugMineService.class);

  private static final double MASTERY_WEIGHT = 0.35;
  private static final double DECAY_WEIGHT = 0.20;
  private static final double RECENT_FAIL_WEIGHT = 0.20;
  private static final double PROFILE_PATTERN_WEIGHT = 0.15;
  private static final double LOW_CONFIDENCE_WEIGHT = 0.10;
  private static final int ECHO_CANDIDATE_LIMIT = 5;
  // shortcut: 10 budget units per new session; recalibrate if measured sandbox cost changes.
  private static final int OPEN_WRITE_COST = 10;
  private static final long COURSE_PACK_ID = 43L;
  private static final String ECHO_ENTRY_CODE =
      "# 遗忘回声入口：重新读题后独立完成同知识点练习\nprint(\"\")\n";

  private final BugMineRepository repository;
  private final PracticeSubmissionRepository submissionRepository;
  private final PublicProblemRepository problemRepository;
  private final PythonTraceCollector traceCollector;
  private final CounterExampleSelector counterExampleSelector;
  private final DebugAgentClient debugAgentClient;
  private final SubmissionService submissionService;
  private final ApplicationEventPublisher eventPublisher;
  private final ObjectMapper objectMapper;
  private final BugMineProfileAdvisor profileAdvisor;
  private final ReviewStateService reviewStateService;
  private final StudentWriteThrottle studentWriteThrottle;
  private final int maxRetries;

  public BugMineService(
      BugMineRepository repository,
      PracticeSubmissionRepository submissionRepository,
      PublicProblemRepository problemRepository,
      PythonTraceCollector traceCollector,
      CounterExampleSelector counterExampleSelector,
      DebugAgentClient debugAgentClient,
      SubmissionService submissionService,
      ApplicationEventPublisher eventPublisher,
      ObjectMapper objectMapper,
      BugMineProfileAdvisor profileAdvisor,
      ReviewStateService reviewStateService,
      StudentWriteThrottle studentWriteThrottle,
      @Value("${pythontopia.bugMine.maxRetriesPerSession:3}") int maxRetries) {
    this.repository = repository;
    this.submissionRepository = submissionRepository;
    this.problemRepository = problemRepository;
    this.traceCollector = traceCollector;
    this.counterExampleSelector = counterExampleSelector;
    this.debugAgentClient = debugAgentClient;
    this.submissionService = submissionService;
    this.eventPublisher = eventPublisher;
    this.objectMapper = objectMapper;
    this.profileAdvisor = profileAdvisor;
    this.reviewStateService = reviewStateService;
    this.studentWriteThrottle = studentWriteThrottle;
    this.maxRetries = maxRetries;
  }

  @Transactional
  public BugMineSessionDto open(Long userId, Long submissionId) {
    return open(userId, submissionId, false);
  }

  private BugMineSessionDto open(Long userId, Long submissionId, boolean writeBudgetReserved) {
    Optional<Long> existing = repository.findSessionIdBySubmission(userId, submissionId);
    if (existing.isPresent()) {
      return enrichSession(
          repository
              .findSession(userId, existing.get())
              .orElseThrow(
                  () ->
                      new BusinessException(
                          HttpStatus.NOT_FOUND, "BUG_MINE_NOT_FOUND", "Bug 矿洞会话不存在")));
    }
    if (!writeBudgetReserved) {
      reserveOpenBudget(userId);
    }

    SubmissionDto submission =
        submissionRepository
            .findSubmission(userId, submissionId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "SUBMISSION_NOT_FOUND",
                        "提交不存在或不属于当前用户"));
    if (submission.status() == SubmissionStatus.PASSED) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "SUBMISSION_ALREADY_PASSED", "已通过的提交无需进入 Bug 矿洞");
    }

    String kcCode = problemRepository.findKcCode(submission.problemId());
    SessionContextRow context = resolveContext(userId, submission.problemId(), submissionId);
    int profileDepthTarget =
        profileAdvisor.recommendedDepthTarget(context.masteryScore(), context.patternMatched());
    Long variantProblemId =
        problemRepository
            .findVariantProblem(userId, submission.problemId(), kcCode)
            .map(PublicProblemRepository.VariantProblem::id)
            .orElse(null);

    Long sessionId =
        repository.insertSession(
            userId,
            submissionId,
            submission.problemId(),
            submission.questId(),
            variantProblemId,
            profileDepthTarget);

    CounterExampleSelector.CounterExample counterExample =
        counterExampleSelector
            .findFirstFailing(submission.problemId(), userId, submission.code())
            .orElseGet(
                () -> new CounterExampleSelector.CounterExample("", "", "", false));

    PythonTraceCollector.TraceResult traceResult =
        counterExample.redacted()
            ? new PythonTraceCollector.TraceResult(emptyTraceSummary(), 0, false)
            : traceCollector.collect(submission.code(), counterExample.stdin());

    Verdict verdict =
        submissionRepository
            .findJudgeResult(submissionId)
            .map(judge -> judge.verdict())
            .orElse(Verdict.WA);
    DebugAgentRequest request =
        new DebugAgentRequest(
            userId,
            submissionId,
            submission.problemId(),
            kcCode == null ? List.of() : List.of(kcCode),
            verdict.name(),
            submission.code(),
            new DebugAgentRequest.CounterExamplePayload(
                counterExample.stdin(),
                counterExample.expected(),
                counterExample.actual(),
                counterExample.redacted()
                    ? "隐藏测试数据已脱敏，请按题面检查边界条件。"
                    : buildDiffSummary(counterExample.expected(), counterExample.actual()),
                counterExample.redacted()),
            new DebugAgentRequest.VariableTracePayload(
                stepsFromTrace(traceResult), traceResult.truncated()),
            List.of());
    DebugAgentResponse response = debugAgentClient.analyse(request);
    if (response == null) {
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY, "DEBUG_GRAPH_EMPTY", "DebugAgent 返回空响应");
    }

    persistCards(sessionId, response, traceResult);
    return enrichSession(
        repository
            .findSession(userId, sessionId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "BUG_MINE_PERSIST_FAILED",
                        "Bug 矿洞会话写入后未能读回")));
  }

  @Transactional(readOnly = true)
  public BugMineSessionDto find(Long userId, Long sessionId) {
    BugMineSessionDto session =
        repository
            .findSession(userId, sessionId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "BUG_MINE_NOT_FOUND", "Bug 矿洞会话不存在"));
    return enrichSession(session);
  }

  @Transactional(readOnly = true)
  public List<BugMineErrorBookItemDto> errorBook(Long userId) {
    return repository.findOpenErrorBook(userId).stream()
        .map(row -> new ScoredError(row, errorSignal(row)))
        .sorted(
            Comparator
                .comparingDouble((ScoredError scored) -> minePriority(scored.row(), scored.signal()))
                .reversed()
                .thenComparing(
                    scored -> scored.row().openedAt(),
                    Comparator.nullsLast(Comparator.reverseOrder())))
        .map(scored -> toErrorBookItem(scored.row(), scored.signal()))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<BugMineEchoCandidateDto> echoCandidates(Long userId) {
    return repository.findEchoCandidates(userId).stream()
        .map(row -> new ScoredEcho(row, echoSignal(row)))
        .filter(scored -> scored.signal().due())
        .sorted(
            Comparator
                .comparingDouble((ScoredEcho scored) -> echoPriority(scored.row(), scored.signal()))
                .reversed()
                .thenComparing(scored -> scored.row().kcCode()))
        .limit(ECHO_CANDIDATE_LIMIT)
        .map(scored -> toEchoCandidate(scored.row(), scored.signal()))
        .toList();
  }

  /**
   * 暴露「记忆矿脉」当下到期（decaying）的 KC 编码，按入矿优先级降序取前 {@code limit} 个。
   *
   * <p>复用 {@link #echoCandidates} 的 FSRS 到期判定与排序作为单一事实来源，供学习总管
   * （OrchestratorAgent）把看/学/练/测计划优先锚定到这些待复习知识点（{@code decaying_kcs} 透传），
   * 形成「记忆矿脉回声 → 总管 → 资源生成」闭环。仅读，不产生副作用。
   */
  @Transactional(readOnly = true)
  public List<String> decayingKcCodes(Long userId, int limit) {
    if (limit <= 0) {
      return List.of();
    }
    return echoCandidates(userId).stream()
        .map(BugMineEchoCandidateDto::kcCode)
        .distinct()
        .limit(limit)
        .toList();
  }

  private ReviewSignal errorSignal(BugMineRepository.ErrorBookRow row) {
    return reviewStateService.evaluate(
        row.kcCode(),
        row.reviewStability(),
        row.reviewLastReviewAt(),
        row.reviewDueAt(),
        doubleValue(row.masteryScore()),
        row.lastEventAt(),
        0,
        0,
        OffsetDateTime.now(ZoneOffset.UTC));
  }

  private ReviewSignal echoSignal(BugMineRepository.EchoCandidateRow row) {
    return reviewStateService.evaluate(
        row.kcCode(),
        row.reviewStability(),
        row.reviewLastReviewAt(),
        row.reviewDueAt(),
        doubleValue(row.masteryScore()),
        row.lastEventAt(),
        0,
        0,
        OffsetDateTime.now(ZoneOffset.UTC));
  }

  private static double doubleValue(BigDecimal value) {
    return value == null ? 0.0 : value.doubleValue();
  }

  private record ScoredError(BugMineRepository.ErrorBookRow row, ReviewSignal signal) {}

  private record ScoredEcho(BugMineRepository.EchoCandidateRow row, ReviewSignal signal) {}

  @Transactional
  public BugMineSessionDto openEcho(Long userId, String kcCode) {
    BugMineRepository.EchoCandidateRow candidate =
        repository.findEchoCandidates(userId).stream()
            .filter(row -> row.kcCode().equals(kcCode))
            .findFirst()
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "BUG_MINE_ECHO_NOT_FOUND",
                        "没有可入矿的遗忘回声 KC"));
    reserveOpenBudget(userId);
    Long submissionId =
        submissionRepository.insertSubmission(
            userId,
            candidate.problemId(),
            null,
            SubmissionSource.BUG_MINE_ECHO,
            ECHO_ENTRY_CODE);
    submissionRepository.markStatus(submissionId, SubmissionStatus.FAILED, null, null);
    ObjectNode trace = emptyTraceSummary();
    trace.put("kcCode", candidate.kcCode());
    trace.put("reason", "forgetting_echo");
    submissionRepository.insertJudgeResult(
        submissionId,
        Verdict.WA,
        0,
        1,
        "遗忘回声复习入口，需要重新确认该 KC。",
        trace);

    BugMineSessionDto opened = open(userId, submissionId, true);
    repository.updateProfileDepthTarget(opened.id(), Math.max(opened.profileDepthTarget(), 4));
    return find(userId, opened.id());
  }

  private void reserveOpenBudget(Long userId) {
    if (!studentWriteThrottle.tryAcquire(userId, OPEN_WRITE_COST)) {
      throw new BusinessException(
          HttpStatus.TOO_MANY_REQUESTS,
          "BUG_MINE_OPEN_THROTTLED",
          "Bug 矿洞开启过于频繁，请稍后再试");
    }
  }

  @Transactional
  public BugMineCardDto reveal(Long userId, Long sessionId, Long cardId) {
    BugMineSessionDto session = find(userId, sessionId);
    BugMineCardDto card =
        repository
            .findCard(session.id(), cardId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "BUG_MINE_CARD_NOT_FOUND", "卡片不存在"));
    int requiredDepth = depthForCard(card.cardType());
    if (session.depthLayer() < requiredDepth) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "BUG_MINE_DEPTH_LOCKED",
          "当前深度 L" + session.depthLayer() + " 不足以解锁该卡片，请先完成上一层或重试失败后再下探。");
    }
    if (card.revealed()) {
      return card;
    }
    repository.revealCard(session.id(), cardId);
    repository.bumpDepthLayer(session.id(), depthForCard(card.cardType()));
    return repository
        .findCard(session.id(), cardId)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "BUG_MINE_REVEAL_FAILED", "卡片解锁失败"));
  }

  @Transactional
  public SubmissionDto retrySubmit(Long userId, Long sessionId, String code) {
    BugMineSessionDto session = find(userId, sessionId);
    if (session.status() != BugMineSessionStatus.OPEN) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "BUG_MINE_SESSION_CLOSED", "Bug 矿洞会话已关闭");
    }
    int currentRetry = repository.retryCount(session.id());
    if (currentRetry >= maxRetries) {
      throw new BusinessException(
          HttpStatus.TOO_MANY_REQUESTS,
          "BUG_MINE_RETRY_EXCEEDED",
          "Bug 矿洞重试次数已达上限 " + maxRetries);
    }
    repository.incrementRetry(session.id());
    SubmissionDto submission =
        submissionService.submit(
            userId,
            new SubmissionCreateRequest(
                session.problemId(),
                code,
                session.questId(),
                SubmissionSource.BUG_MINE_RETRY));
    if (submission.status() == SubmissionStatus.PASSED) {
      resolveSession(userId, session.id(), session.problemId(), submission.id(), false);
    } else {
      repository.advanceDepthLayer(session.id());
    }
    return submission;
  }

  @Transactional
  public SubmissionDto variantSubmit(Long userId, Long sessionId, String code) {
    BugMineSessionDto session = find(userId, sessionId);
    if (session.status() != BugMineSessionStatus.OPEN) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "BUG_MINE_SESSION_CLOSED", "Bug 矿洞会话已关闭");
    }
    if (session.depthLayer() < 5) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "BUG_MINE_VARIANT_LOCKED",
          "需下探到 L5 才能提交同 KC 变式掌握确认。");
    }
    if (session.variantProblem() == null) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "BUG_MINE_VARIANT_MISSING", "当前会话没有可用的同 KC 变式题。");
    }
    SubmissionDto submission =
        submissionService.submit(
            userId,
            new SubmissionCreateRequest(
                session.variantProblem().id(),
                code,
                session.questId(),
                SubmissionSource.BUG_MINE_VARIANT));
    repository.updateVariantSubmission(session.id(), submission.id());
    if (submission.status() == SubmissionStatus.PASSED) {
      repository.markMasteryConfirmed(session.id());
      resolveSession(userId, session.id(), session.variantProblem().id(), submission.id(), true);
    }
    return submission;
  }

  @Transactional
  public BugMineSessionDto close(Long userId, Long sessionId, BugMineSessionStatus status) {
    BugMineSessionDto session = find(userId, sessionId);
    if (status == BugMineSessionStatus.OPEN) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "BUG_MINE_INVALID_CLOSE", "无法把会话状态置回 OPEN");
    }
    // 终态守卫：已 RESOLVED/ABANDONED 的会话不可再关闭，否则重复 POST 会反复发 PracticeAbandoned
    // （反复改画像、写记忆流），还能把已解决的会话改判成放弃。
    if (session.status() != BugMineSessionStatus.OPEN) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "BUG_MINE_SESSION_CLOSED", "Bug 矿洞会话已关闭");
    }
    // 并发下 SQL 的 status='OPEN' 谓词才是真正的仲裁者：影响 0 行 = 已被别的请求先关闭。
    if (repository.updateStatus(session.id(), status) == 0) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "BUG_MINE_SESSION_CLOSED", "Bug 矿洞会话已关闭");
    }
    if (status == BugMineSessionStatus.ABANDONED) {
      String kcCode = problemRepository.findKcCode(session.problemId());
      eventPublisher.publishEvent(
          new LearningEvent.PracticeAbandoned(userId, session.problemId(), kcCode));
    }
    return enrichSession(
        repository
            .findSession(userId, sessionId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "BUG_MINE_CLOSE_FAILED", "关闭会话失败")));
  }

  private void resolveSession(
      Long userId, Long sessionId, Long problemId, Long submissionId, boolean masteryConfirmed) {
    if (repository.updateStatus(sessionId, BugMineSessionStatus.RESOLVED) == 0) {
      // 会话已是终态（并发请求先一步关闭）：不重复发 DebugResolved 事件。
      log.warn("Bug mine session {} already in terminal state; skipping DebugResolved", sessionId);
      return;
    }
    if (masteryConfirmed) {
      repository.markMasteryConfirmed(sessionId);
    }
    String kcCode = problemRepository.findKcCode(problemId);
    if (kcCode != null && !kcCode.isBlank()) {
      eventPublisher.publishEvent(
          new LearningEvent.DebugResolved(
              userId, kcCode, problemId, "submission:" + submissionId));
    }
  }

  private BugMineErrorBookItemDto toErrorBookItem(
      BugMineRepository.ErrorBookRow row, ReviewSignal signal) {
    String guidance =
        profileAdvisor.guidanceLabel(row.profileDepthTarget(), row.patternMatched());
    BugMineVariantProblemDto variant = toVariant(row.variantProblemId(), row.variantProblemTitle());
    double decayRisk = decayRisk(signal);
    double priority = minePriority(row, signal);
    return new BugMineErrorBookItemDto(
        row.sessionId(),
        row.submissionId(),
        row.problemId(),
        row.questId(),
        row.kcCode(),
        row.problemTitle(),
        row.status(),
        row.retryCount(),
        row.depthLayer(),
        row.profileDepthTarget(),
        guidance,
        row.masteryScore(),
        scaled(priority),
        scaled(decayRisk),
        recommendationReason(row, signal),
        row.mistakePatterns(),
        row.patternMatched(),
        row.openedAt(),
        variant,
        depthSteps(row.depthLayer()));
  }

  private double minePriority(BugMineRepository.ErrorBookRow row, ReviewSignal signal) {
    double mastery = clamp(row.masteryScore() == null ? 0.0 : row.masteryScore().doubleValue());
    double confidence = clamp(row.confidence() == null ? 0.0 : row.confidence().doubleValue());
    double recentFail = recentFailWeight(row.openedAt());
    return clamp(
        MASTERY_WEIGHT * (1.0 - mastery)
            + DECAY_WEIGHT * decayRisk(signal)
            + RECENT_FAIL_WEIGHT * recentFail
            + PROFILE_PATTERN_WEIGHT * (row.patternMatched() ? 1.0 : 0.0)
            + LOW_CONFIDENCE_WEIGHT * (1.0 - confidence));
  }

  /** 遗忘风险 = 1 - 可提取性 R（FSRS-lite），替换原先全局 14 天半衰期。 */
  private double decayRisk(ReviewSignal signal) {
    return clamp(1.0 - signal.retrievability());
  }

  private BugMineEchoCandidateDto toEchoCandidate(
      BugMineRepository.EchoCandidateRow row, ReviewSignal signal) {
    double decayRisk = decayRisk(signal);
    double priority = echoPriority(row, signal);
    return new BugMineEchoCandidateDto(
        row.kcCode(),
        row.problemId(),
        row.problemTitle(),
        row.masteryScore(),
        row.confidence(),
        scaled(decayRisk),
        scaled(priority),
        echoRecommendationReason(row, signal));
  }

  private double echoPriority(BugMineRepository.EchoCandidateRow row, ReviewSignal signal) {
    double mastery = clamp(row.masteryScore() == null ? 0.0 : row.masteryScore().doubleValue());
    double confidence = clamp(row.confidence() == null ? 0.0 : row.confidence().doubleValue());
    return clamp(
        0.45 * decayRisk(signal)
            + 0.25 * (1.0 - confidence)
            + 0.20 * (1.0 - mastery)
            + 0.10);
  }

  private String echoRecommendationReason(
      BugMineRepository.EchoCandidateRow row, ReviewSignal signal) {
    double confidence = clamp(row.confidence() == null ? 0.0 : row.confidence().doubleValue());
    StringBuilder reason = new StringBuilder("遗忘回声");
    if (confidence < 0.40) {
      reason.append(" · 置信度低");
    }
    if (decayRisk(signal) >= 0.50) {
      reason.append(" · 间隔过久");
    }
    reason.append(" · 建议入矿");
    return reason.toString();
  }

  private double recentFailWeight(OffsetDateTime openedAt) {
    if (openedAt == null) {
      return 0.0;
    }
    double hours =
        Math.max(
            0.0,
            Duration.between(openedAt, OffsetDateTime.now(ZoneOffset.UTC)).toMinutes() / 60.0);
    if (hours <= 24.0) {
      return 1.0;
    }
    if (hours >= 168.0) {
      return 0.0;
    }
    return clamp(1.0 - ((hours - 24.0) / 144.0));
  }

  private String recommendationReason(BugMineRepository.ErrorBookRow row, ReviewSignal signal) {
    double mastery = clamp(row.masteryScore() == null ? 0.0 : row.masteryScore().doubleValue());
    double confidence = clamp(row.confidence() == null ? 0.0 : row.confidence().doubleValue());
    boolean due = signal.due();
    StringBuilder reason = new StringBuilder();
    if (mastery < 0.45) {
      reason.append("掌握薄弱");
    } else if (due) {
      reason.append("遗忘回声");
    } else if (row.patternMatched()) {
      reason.append("画像命中");
    } else {
      reason.append("近期失败");
    }
    if (row.patternMatched() && reason.indexOf("画像命中") < 0) {
      reason.append(" · 画像命中");
    }
    if (confidence < 0.40) {
      reason.append(" · 置信度低");
    }
    if (due && reason.indexOf("遗忘回声") < 0) {
      reason.append(" · 遗忘回声");
    }
    reason.append(" · 建议目标 L").append(row.profileDepthTarget());
    return reason.toString();
  }

  private BigDecimal scaled(double value) {
    return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP);
  }

  private double clamp(double value) {
    return Math.max(0.0, Math.min(1.0, value));
  }

  private BugMineSessionDto enrichSession(BugMineSessionDto session) {
    SessionContextRow context =
        resolveContext(session.userId(), session.problemId(), session.submissionId());
    int depthTarget =
        session.profileDepthTarget() > 0
            ? session.profileDepthTarget()
            : profileAdvisor.recommendedDepthTarget(
                context.masteryScore(), context.patternMatched());
    boolean echoSession =
        repository
            .findSubmissionSource(session.submissionId())
            .filter(source -> source == SubmissionSource.BUG_MINE_ECHO)
            .isPresent();
    String guidance =
        echoSession
            ? profileAdvisor.echoGuidanceLabel(depthTarget)
            : profileAdvisor.guidanceLabel(depthTarget, context.patternMatched());
    // 题干上下文随会话一次性下发：客户端无需再请求 /api/problems/{id}/public。
    PublicProblemDto problem =
        problemRepository
            .findAccessible(COURSE_PACK_ID, session.problemId(), session.userId())
            .orElse(null);
    return new BugMineSessionDto(
        session.id(),
        session.userId(),
        session.submissionId(),
        session.problemId(),
        session.questId(),
        session.status(),
        session.retryCount(),
        session.depthLayer(),
        depthTarget,
        session.masteryConfirmed(),
        session.variantSubmissionId(),
        session.openedAt(),
        session.closedAt(),
        session.variantProblem(),
        guidance,
        depthSteps(session.depthLayer()),
        session.cards(),
        problem);
  }

  private SessionContextRow resolveContext(Long userId, Long problemId, Long submissionId) {
    return repository
        .findSessionContext(userId, problemId, submissionId)
        .orElse(new SessionContextRow(BigDecimal.ZERO, List.of(), false));
  }

  private BugMineVariantProblemDto toVariant(Long variantProblemId, String title) {
    if (variantProblemId == null) {
      return null;
    }
    return new BugMineVariantProblemDto(variantProblemId, title);
  }

  private List<DebugAgentRequest.TraceStep> stepsFromTrace(PythonTraceCollector.TraceResult trace) {
    JsonNode stepsNode = trace.summary().get("steps");
    List<DebugAgentRequest.TraceStep> out = new java.util.ArrayList<>();
    if (stepsNode != null && stepsNode.isArray()) {
      for (JsonNode entry : stepsNode) {
        int line = entry.path("line").asInt();
        java.util.Map<String, String> locals = new java.util.LinkedHashMap<>();
        JsonNode localsNode = entry.get("locals");
        if (localsNode != null) {
          localsNode
              .fields()
              .forEachRemaining(
                  field -> locals.put(field.getKey(), field.getValue().asText()));
        }
        out.add(new DebugAgentRequest.TraceStep(line, locals));
        if (out.size() >= 200) {
          break;
        }
      }
    }
    return out;
  }

  private void persistCards(
      Long sessionId, DebugAgentResponse response, PythonTraceCollector.TraceResult trace) {
    persistCard(sessionId, BugMineCardType.COUNTER_EXAMPLE, response.counterExample(), false);
    persistCard(sessionId, BugMineCardType.VARIABLE_TRACE, trace.summary(), false);
    persistCard(sessionId, BugMineCardType.WRONG_ASSUMPTION, response.wrongAssumption(), false);
    List<DebugAgentResponse.HintLayer> layers = response.hintLayers();
    for (DebugAgentResponse.HintLayer layer : layers) {
      BugMineCardType type =
          switch (layer.layer()) {
            case 1 -> BugMineCardType.HINT_LAYER_1;
            case 2 -> BugMineCardType.HINT_LAYER_2;
            case 3 -> BugMineCardType.HINT_LAYER_3;
            default -> throw new IllegalStateException("Unknown hint layer " + layer.layer());
          };
      persistCard(sessionId, type, layer, false);
    }
  }

  private void persistCard(Long sessionId, BugMineCardType type, Object payload, boolean revealed) {
    JsonNode node = objectMapper.valueToTree(payload);
    repository.insertCard(sessionId, type, node, revealed);
  }

  private List<BugMineDepthStepDto> depthSteps(int depthLayer) {
    return List.of(
        depthStep(1, "bare_retry", "L1 裸重试", "不看提示，先按题意重写一次。", depthLayer),
        depthStep(2, "counter_example", "L2 反例", "查看第一个失败输入与期望输出。", depthLayer),
        depthStep(3, "variable_trace", "L3 变量轨迹", "沿执行过程检查变量如何变化。", depthLayer),
        depthStep(4, "layered_hints", "L4 分层提示", "逐层打开提示，只拿思路不拿完整答案。", depthLayer),
        depthStep(5, "same_kc_variant", "L5 同 KC 变式", "换一道同知识点题确认迁移能力。", depthLayer));
  }

  private BugMineDepthStepDto depthStep(
      int layer, String key, String title, String description, int depthLayer) {
    return new BugMineDepthStepDto(layer, key, title, description, layer <= depthLayer);
  }

  private int depthForCard(BugMineCardType type) {
    return switch (type) {
      case COUNTER_EXAMPLE -> 2;
      case VARIABLE_TRACE -> 3;
      case WRONG_ASSUMPTION, HINT_LAYER_1, HINT_LAYER_2, HINT_LAYER_3 -> 4;
    };
  }

  private String buildDiffSummary(String expected, String actual) {
    String[] e = expected == null ? new String[0] : expected.split("\\n", -1);
    String[] a = actual == null ? new String[0] : actual.split("\\n", -1);
    int max = Math.max(e.length, a.length);
    int reported = 0;
    StringBuilder diffSummary = new StringBuilder();
    for (int i = 0; i < max && reported < 3; i++) {
      String le = i < e.length ? e[i] : "";
      String la = i < a.length ? a[i] : "";
      if (!le.equals(la)) {
        if (diffSummary.length() > 0) diffSummary.append("; ");
        diffSummary
            .append("第 ")
            .append(i + 1)
            .append(" 行: 期望 '")
            .append(le)
            .append("' 实际 '")
            .append(la)
            .append("'");
        reported += 1;
      }
    }
    if (diffSummary.length() == 0) {
      return "尾部空白或空行差异";
    }
    return diffSummary.toString();
  }

  private ObjectNode emptyTraceSummary() {
    ObjectNode node = objectMapper.createObjectNode();
    node.set("steps", objectMapper.createArrayNode());
    node.put("truncated", false);
    return node;
  }
}
