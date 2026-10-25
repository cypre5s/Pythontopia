package com.pythontopia.learner.evaluation.service;

import com.pythontopia.common.error.BusinessException;
import com.pythontopia.coursepack.domain.CourseKc;
import com.pythontopia.coursepack.repository.CoursePackRepository;
import com.pythontopia.learner.evaluation.dto.EvaluationActedStatus;
import com.pythontopia.learner.evaluation.dto.EvaluationDto;
import com.pythontopia.learner.evaluation.dto.EvaluationMetricDto;
import com.pythontopia.learner.evaluation.dto.EvaluationRequest;
import com.pythontopia.learner.evaluation.graph.EvaluationGraphClient;
import com.pythontopia.learner.evaluation.graph.EvaluationNarrativeRequest;
import com.pythontopia.learner.evaluation.graph.EvaluationNarrativeResponse;
import com.pythontopia.learner.evaluation.repository.EvaluationRepository;
import com.pythontopia.learner.evaluation.repository.EvaluationRepository.KcDelta;
import com.pythontopia.learner.evaluation.repository.EvaluationRepository.RecentEvent;
import com.pythontopia.learner.path.dto.LearningPathDto;
import com.pythontopia.learner.path.graph.PathPlannerGraphResponse;
import com.pythontopia.learner.path.service.LearningPathService;
import com.pythontopia.learner.profile.dto.LearnerProfileDto;
import com.pythontopia.learner.profile.service.ProfileService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class LearnerEvaluationService {

  static final Long DEFAULT_PACK_ID = 43L;
  static final int DEFAULT_PERIOD_DAYS = 7;
  // 每周自动生成的去重窗口：近 6 天内已有评估（自动或手动）则本周跳过，保证幂等可重跑。
  static final int WEEKLY_DEDUPE_DAYS = 6;
  static final int HISTORY_LIMIT_DEFAULT = 8;
  static final int HISTORY_LIMIT_MAX = 30;
  static final int TOP_KC_LIMIT = 5;
  static final int RECENT_EVENT_LIMIT = 3;

  private final EvaluationRepository evaluationRepository;
  private final EvaluationGraphClient narrativeClient;
  private final ProfileService profileService;
  private final LearningPathService pathService;
  private final CoursePackRepository coursePackRepository;
  private final TransactionTemplate transaction;

  public LearnerEvaluationService(
      EvaluationRepository evaluationRepository,
      EvaluationGraphClient narrativeClient,
      ProfileService profileService,
      LearningPathService pathService,
      CoursePackRepository coursePackRepository,
      PlatformTransactionManager transactionManager) {
    this.evaluationRepository = evaluationRepository;
    this.narrativeClient = narrativeClient;
    this.profileService = profileService;
    this.pathService = pathService;
    this.coursePackRepository = coursePackRepository;
    this.transaction = new TransactionTemplate(transactionManager);
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public EvaluationDto compute(Long userId, EvaluationRequest request) {
    int periodDays =
        request != null && request.periodDays() != null ? request.periodDays() : DEFAULT_PERIOD_DAYS;
    if (periodDays < 1 || periodDays > 30) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "EVALUATION_PERIOD_INVALID", "periodDays 必须在 1~30 之间");
    }
    OffsetDateTime periodEnd = OffsetDateTime.now(ZoneOffset.UTC);
    OffsetDateTime periodStart = periodEnd.minusDays(periodDays);

    LearnerProfileDto profile = profileService.findProfile(userId).orElse(null);
    Map<String, String> kcNames = loadKcNames();

    BigDecimal masteryTotal = scale(evaluationRepository.sumMasteryTotal(userId));
    BigDecimal previousTotal =
        evaluationRepository.findPreviousMasterySum(userId).orElse(BigDecimal.ZERO);
    BigDecimal masteryDelta = scale(masteryTotal.subtract(previousTotal));

    int totalSubmissions = evaluationRepository.countSubmissionsAfter(userId, periodStart);
    int passedSubmissions = evaluationRepository.countAcceptedSubmissionsAfter(userId, periodStart);
    BigDecimal passRate =
        totalSubmissions == 0
            ? BigDecimal.ZERO
            : scale(
                BigDecimal.valueOf(passedSubmissions)
                    .divide(BigDecimal.valueOf(totalSubmissions), 4, RoundingMode.HALF_UP));

    int bugMineOpened = evaluationRepository.countBugMineOpenedAfter(userId, periodStart);
    int bugMineResolved = evaluationRepository.countBugMineResolvedAfter(userId, periodStart);
    BigDecimal resolveRate =
        bugMineOpened == 0
            ? BigDecimal.ZERO
            : scale(
                BigDecimal.valueOf(bugMineResolved)
                    .divide(BigDecimal.valueOf(bugMineOpened), 4, RoundingMode.HALF_UP));

    int positiveRevisions =
        evaluationRepository.countProfileRevisionsBySourceAfter(userId, "PRACTICE_PASS", periodStart)
            + evaluationRepository.countProfileRevisionsBySourceAfter(
                userId, "DEBUG_FIX", periodStart);
    int negativeRevisions =
        evaluationRepository.countProfileRevisionsBySourceAfter(userId, "PRACTICE_FAIL", periodStart);
    int totalRevisions = positiveRevisions + negativeRevisions;
    BigDecimal confidenceDelta =
        totalRevisions == 0
            ? BigDecimal.ZERO
            : scale(
                BigDecimal.valueOf(positiveRevisions - negativeRevisions)
                    .divide(BigDecimal.valueOf(totalRevisions), 4, RoundingMode.HALF_UP));

    int feedbackCount = evaluationRepository.countResourceFeedbackAfter(userId, periodStart);
    int distinctKcCount = evaluationRepository.countDistinctResourceKcAfter(userId, periodStart);
    BigDecimal resourceConsumption =
        BigDecimal.valueOf((long) feedbackCount + distinctKcCount).setScale(4, RoundingMode.HALF_UP);

    List<KcDelta> topKcs = evaluationRepository.findTopKcDelta(userId, periodStart, TOP_KC_LIMIT);
    List<RecentEvent> recentEvents =
        evaluationRepository.findRecentEvents(userId, periodStart, RECENT_EVENT_LIMIT);

    List<EvaluationMetricDto> metrics =
        buildMetrics(
            masteryTotal,
            masteryDelta,
            passRate,
            passedSubmissions,
            totalSubmissions,
            resolveRate,
            bugMineResolved,
            bugMineOpened,
            confidenceDelta,
            positiveRevisions,
            totalRevisions,
            resourceConsumption);

    boolean hasActivity =
        totalSubmissions > 0
            || bugMineOpened > 0
            || feedbackCount > 0
            || distinctKcCount > 0
            || !topKcs.isEmpty();

    EvaluationNarrativeRequest narrativeRequest =
        new EvaluationNarrativeRequest(
            userId,
            DEFAULT_PACK_ID,
            mapMetrics(metrics),
            mapTopKcs(topKcs, kcNames),
            mapEvents(recentEvents),
            profileBrief(profile, totalSubmissions > 0 && passRate.compareTo(new BigDecimal("0.5")) < 0));
    EvaluationNarrativeResponse narrative = narrativeClient.narrate(narrativeRequest);
    if (narrative.safetyResult() != null && !narrative.safetyResult().passed()) {
      throw new BusinessException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "EVALUATION_NARRATIVE_SAFETY_FAILED",
          String.join(",", narrative.safetyResult().reasons() == null
              ? List.of()
              : narrative.safetyResult().reasons()));
    }
    if (narrative.groundingResult() != null && !narrative.groundingResult().passed()) {
      throw new BusinessException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "EVALUATION_NARRATIVE_GROUNDING_FAILED",
          String.join(",", narrative.groundingResult().reasons() == null
              ? List.of()
              : narrative.groundingResult().reasons()));
    }

    LearningPathDto activePath = pathService.findActive(userId).orElse(null);
    Long activePathId = activePath == null ? null : activePath.id();

    String diffSummary = narrative.pathDiffExplainer() == null ? "" : narrative.pathDiffExplainer();
    PathPlannerGraphResponse preparedDraft = null;
    if (hasActivity && profile != null) {
      try {
        preparedDraft = pathService.prepareDraft(userId);
      } catch (BusinessException ex) {
        // path planner 失败不会阻塞评估，但会在 diff_summary 注明
        diffSummary = diffSummary + "\n[规划失败] " + ex.getMessage();
      }
    } else {
      diffSummary = diffSummary.isBlank() ? "近期数据不足，未生成新路径建议。" : diffSummary;
    }

    PathPlannerGraphResponse draftResponse = preparedDraft;
    String pathDiffSummary = diffSummary;
    return transaction.execute(
        status -> {
          Long draftPathId =
              draftResponse == null ? null : pathService.persistDraft(userId, draftResponse).id();
          Long evaluationId =
              evaluationRepository.insert(
                  userId,
                  DEFAULT_PACK_ID,
                  periodStart,
                  periodEnd,
                  narrative.narrativeMd() == null ? "" : narrative.narrativeMd(),
                  pathDiffSummary,
                  draftPathId,
                  activePathId);

          for (EvaluationMetricDto metric : metrics) {
            evaluationRepository.insertMetric(evaluationId, metric);
          }

          LinkedHashMap<String, Object> revisionDiff = new LinkedHashMap<>();
          revisionDiff.put("evaluationId", evaluationId);
          revisionDiff.put("narrativeRef", "evaluation:" + evaluationId);
          revisionDiff.put("recommendations", narrative.keyRecommendations());
          profileService.applyPassiveRevision(userId, "EVALUATION", revisionDiff, evaluationId);

          return findById(userId, evaluationId);
        });
  }

  @Transactional(readOnly = true)
  public EvaluationDto findById(Long userId, Long evaluationId) {
    EvaluationDto base =
        evaluationRepository
            .findById(userId, evaluationId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "EVALUATION_NOT_FOUND",
                        "未找到该评估或无权访问"));
    LearningPathDto draft = null;
    if (base.draftPathId() != null) {
      draft = pathService.findById(userId, base.draftPathId());
    }
    return new EvaluationDto(
        base.id(),
        base.userId(),
        base.packId(),
        base.periodStart(),
        base.periodEnd(),
        base.narrativeSummary(),
        base.pathDiffSummary(),
        base.draftPathId(),
        base.activePathId(),
        base.createdAt(),
        base.actedStatus(),
        base.metrics(),
        draft);
  }

  @Transactional(readOnly = true)
  public List<EvaluationDto> listForUser(Long userId, Integer limit) {
    int effective = limit == null ? HISTORY_LIMIT_DEFAULT : Math.min(HISTORY_LIMIT_MAX, Math.max(1, limit));
    return evaluationRepository.listForUser(userId, effective);
  }

  /**
   * 每周自动生成用：近 {@value #DEFAULT_PERIOD_DAYS} 天有学习活动、且近
   * {@value #WEEKLY_DEDUPE_DAYS} 天内尚无评估（自动或手动）的学生 user_id 列表。
   * 仅做只读筛选，实际生成由调度器逐个调 {@link #compute}（各自独立事务、单点失败隔离）。
   */
  @Transactional(readOnly = true)
  public List<Long> findLearnersNeedingWeeklyEvaluation() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    OffsetDateTime activitySince = now.minusDays(DEFAULT_PERIOD_DAYS);
    OffsetDateTime dedupeSince = now.minusDays(WEEKLY_DEDUPE_DAYS);
    return evaluationRepository.findActiveLearnerIdsWithoutRecentEvaluation(
        activitySince, dedupeSince);
  }

  @Transactional
  public EvaluationDto acceptNewPath(Long userId, Long evaluationId) {
    EvaluationDto evaluation = findById(userId, evaluationId);
    if (evaluation.actedStatus() != EvaluationActedStatus.PENDING) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "EVALUATION_ALREADY_ACTED",
          "该评估已处理，无法重复操作");
    }
    if (evaluation.draftPathId() == null) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "EVALUATION_NO_DRAFT",
          "该评估未生成可激活的 DRAFT 路径");
    }
    if (evaluationRepository.claimPendingStatus(
            userId, evaluationId, EvaluationActedStatus.ACCEPTED)
        == 0) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "EVALUATION_ALREADY_ACTED",
          "该评估已处理，无法重复操作");
    }
    pathService.promoteDraft(userId, evaluation.draftPathId());
    return findById(userId, evaluationId);
  }

  @Transactional
  public EvaluationDto keepOld(Long userId, Long evaluationId) {
    EvaluationDto evaluation = findById(userId, evaluationId);
    if (evaluation.actedStatus() != EvaluationActedStatus.PENDING) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "EVALUATION_ALREADY_ACTED",
          "该评估已处理，无法重复操作");
    }
    if (evaluationRepository.claimPendingStatus(
            userId, evaluationId, EvaluationActedStatus.KEPT_OLD)
        == 0) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "EVALUATION_ALREADY_ACTED",
          "该评估已处理，无法重复操作");
    }
    if (evaluation.draftPathId() != null) {
      pathService.archiveDraft(userId, evaluation.draftPathId());
    }
    return findById(userId, evaluationId);
  }

  private List<EvaluationMetricDto> buildMetrics(
      BigDecimal masteryTotal,
      BigDecimal masteryDelta,
      BigDecimal passRate,
      int passedSubmissions,
      int totalSubmissions,
      BigDecimal resolveRate,
      int resolvedSessions,
      int openedSessions,
      BigDecimal confidenceDelta,
      int positiveRevisions,
      int totalRevisions,
      BigDecimal resourceConsumption) {
    List<EvaluationMetricDto> out = new ArrayList<>();
    out.add(metric(
        "bug_mine_resolve_rate",
        BigDecimal.valueOf(resolvedSessions),
        BigDecimal.valueOf(openedSessions),
        resolveRate,
        "ratio",
        "周期内 Bug 矿洞修复率"));
    out.add(metric(
        "kc_mastery_delta_total",
        masteryDelta.abs(),
        BigDecimal.ZERO,
        masteryDelta,
        "score",
        "周期内 KC 掌握增量"));
    out.add(metric(
        "kc_mastery_total",
        masteryTotal,
        BigDecimal.ZERO,
        masteryTotal,
        "score",
        "当前 KC 掌握度之和"));
    out.add(metric(
        "practice_pass_rate",
        BigDecimal.valueOf(passedSubmissions),
        BigDecimal.valueOf(totalSubmissions),
        passRate,
        "ratio",
        "周期内练习 AC 占比"));
    out.add(metric(
        "profile_confidence_delta",
        BigDecimal.valueOf(positiveRevisions),
        BigDecimal.valueOf(totalRevisions),
        confidenceDelta,
        "score",
        "周期内画像置信度增量"));
    out.add(metric(
        "resource_consumption_count",
        resourceConsumption,
        BigDecimal.ZERO,
        resourceConsumption,
        "count",
        "周期内消化的学习资源数"));
    out.sort(Comparator.comparing(EvaluationMetricDto::metricKey));
    return out;
  }

  private EvaluationMetricDto metric(
      String key,
      BigDecimal numerator,
      BigDecimal denominator,
      BigDecimal value,
      String unit,
      String notes) {
    return new EvaluationMetricDto(
        key, scale(numerator), scale(denominator), scale(value), unit, notes);
  }

  private List<EvaluationNarrativeRequest.Metric> mapMetrics(List<EvaluationMetricDto> metrics) {
    List<EvaluationNarrativeRequest.Metric> out = new ArrayList<>();
    for (EvaluationMetricDto metric : metrics) {
      out.add(new EvaluationNarrativeRequest.Metric(
          metric.metricKey(),
          metric.numerator() == null ? 0.0 : metric.numerator().doubleValue(),
          metric.denominator() == null ? 0.0 : metric.denominator().doubleValue(),
          metric.value() == null ? 0.0 : metric.value().doubleValue(),
          metric.displayUnit() == null ? "" : metric.displayUnit(),
          metric.notes() == null ? "" : metric.notes()));
    }
    return out;
  }

  private List<EvaluationNarrativeRequest.KcSummary> mapTopKcs(
      List<KcDelta> kcDeltas, Map<String, String> kcNames) {
    List<EvaluationNarrativeRequest.KcSummary> out = new ArrayList<>();
    for (KcDelta delta : kcDeltas) {
      out.add(new EvaluationNarrativeRequest.KcSummary(
          delta.kcCode(),
          kcNames.getOrDefault(delta.kcCode(), delta.kcCode()),
          delta.masteryScore() == null ? 0.0 : delta.masteryScore().doubleValue(),
          delta.deltaScore() == null ? 0.0 : delta.deltaScore().doubleValue()));
    }
    return out;
  }

  private List<EvaluationNarrativeRequest.EventSummary> mapEvents(List<RecentEvent> events) {
    List<EvaluationNarrativeRequest.EventSummary> out = new ArrayList<>();
    for (RecentEvent event : events) {
      String occurred = event.occurredAt() == null ? "" : event.occurredAt().toString();
      out.add(new EvaluationNarrativeRequest.EventSummary(
          event.kind(), occurred, event.detail() == null ? "" : event.detail()));
    }
    return out;
  }

  private EvaluationNarrativeRequest.ProfileBrief profileBrief(
      LearnerProfileDto profile, boolean hasRecentFailures) {
    if (profile == null) {
      return new EvaluationNarrativeRequest.ProfileBrief("OTHER", 2, hasRecentFailures);
    }
    String major = profile.major() == null ? "OTHER" : profile.major();
    int level = 2;
    if (profile.baseLevel() != null && profile.baseLevel().pythonSelfRatedLevel() != null) {
      level = Math.max(1, Math.min(5, profile.baseLevel().pythonSelfRatedLevel()));
    }
    return new EvaluationNarrativeRequest.ProfileBrief(major, level, hasRecentFailures);
  }

  private Map<String, String> loadKcNames() {
    Map<String, String> index = new HashMap<>();
    for (CourseKc kc : coursePackRepository.findKcs(DEFAULT_PACK_ID, null)) {
      index.put(kc.kcCode(), kc.kcName());
    }
    return index;
  }

  private static BigDecimal scale(BigDecimal value) {
    return Optional.ofNullable(value).orElse(BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);
  }
}
