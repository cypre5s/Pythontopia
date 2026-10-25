package com.pythontopia.learner.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.pythontopia.learner.evaluation.service.LearnerEvaluationService;
import com.pythontopia.learner.path.dto.LearningPathDto;
import com.pythontopia.learner.path.dto.LearningPathStatus;
import com.pythontopia.learner.path.graph.PathPlannerGraphResponse;
import com.pythontopia.learner.path.service.LearningPathService;
import com.pythontopia.learner.profile.dto.LearnerProfileDto;
import com.pythontopia.learner.profile.service.ProfileService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class LearnerEvaluationServiceTest {

  private final EvaluationRepository repository = mock(EvaluationRepository.class);
  private final EvaluationGraphClient narrativeClient = mock(EvaluationGraphClient.class);
  private final ProfileService profileService = mock(ProfileService.class);
  private final LearningPathService pathService = mock(LearningPathService.class);
  private final CoursePackRepository coursePackRepository = mock(CoursePackRepository.class);
  private final PlatformTransactionManager transactionManager = new TestTransactionManager();
  private LearnerEvaluationService service;

  @BeforeEach
  void createTransactionalProxy() {
    LearnerEvaluationService target =
        new LearnerEvaluationService(
            repository,
            narrativeClient,
            profileService,
            pathService,
            coursePackRepository,
            transactionManager);
    ProxyFactory proxyFactory = new ProxyFactory(target);
    proxyFactory.addAdvice(
        new TransactionInterceptor(
            transactionManager, new AnnotationTransactionAttributeSource()));
    service = (LearnerEvaluationService) proxyFactory.getProxy();
  }

  @Test
  void computeWithoutActivityReturnsPartialAndSkipsDraft() {
    AtomicBoolean transactionDuringNarrativeCall = new AtomicBoolean(true);
    AtomicBoolean transactionDuringEvaluationWrite = new AtomicBoolean(false);
    when(profileService.findProfile(7L)).thenReturn(Optional.empty());
    when(coursePackRepository.findKcs(eq(43L), eq(null))).thenReturn(List.of());
    when(repository.sumMasteryTotal(7L)).thenReturn(BigDecimal.ZERO);
    when(repository.findPreviousMasterySum(7L)).thenReturn(Optional.empty());
    when(repository.countSubmissionsAfter(eq(7L), any())).thenReturn(0);
    when(repository.countAcceptedSubmissionsAfter(eq(7L), any())).thenReturn(0);
    when(repository.countBugMineOpenedAfter(eq(7L), any())).thenReturn(0);
    when(repository.countBugMineResolvedAfter(eq(7L), any())).thenReturn(0);
    when(repository.countProfileRevisionsBySourceAfter(eq(7L), any(), any())).thenReturn(0);
    when(repository.countResourceFeedbackAfter(eq(7L), any())).thenReturn(0);
    when(repository.countDistinctResourceKcAfter(eq(7L), any())).thenReturn(0);
    when(repository.findTopKcDelta(eq(7L), any(), anyInt())).thenReturn(List.of());
    when(repository.findRecentEvents(eq(7L), any(), anyInt())).thenReturn(List.of());
    when(narrativeClient.narrate(any(EvaluationNarrativeRequest.class)))
        .thenAnswer(
            invocation -> {
              transactionDuringNarrativeCall.set(
                  TransactionSynchronizationManager.isActualTransactionActive());
              return idleNarrative();
            });
    when(pathService.findActive(7L)).thenReturn(Optional.empty());
    when(repository.insert(eq(7L), eq(43L), any(), any(), any(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              transactionDuringEvaluationWrite.set(
                  TransactionSynchronizationManager.isActualTransactionActive());
              return 101L;
            });
    when(repository.findById(7L, 101L))
        .thenReturn(Optional.of(persisted(101L, null, null, EvaluationActedStatus.PENDING)));

    EvaluationDto dto = service.compute(7L, new EvaluationRequest(7));

    assertThat(transactionDuringNarrativeCall).isFalse();
    assertThat(transactionDuringEvaluationWrite).isTrue();
    assertThat(dto.id()).isEqualTo(101L);
    assertThat(dto.actedStatus()).isEqualTo(EvaluationActedStatus.PENDING);
    verify(pathService, never()).prepareDraft(anyLong());
    verify(repository, times(6)).insertMetric(eq(101L), any(EvaluationMetricDto.class));
    verify(profileService).applyPassiveRevision(eq(7L), eq("EVALUATION"), any(), eq(101L));
  }

  @Test
  void computeWithActivityGeneratesDraftPath() {
    AtomicBoolean transactionDuringNarrativeCall = new AtomicBoolean(true);
    AtomicBoolean transactionDuringPathPlanning = new AtomicBoolean(true);
    AtomicBoolean transactionDuringPathPersistence = new AtomicBoolean(false);
    LearnerProfileDto profile = profile();
    when(profileService.findProfile(7L)).thenReturn(Optional.of(profile));
    when(coursePackRepository.findKcs(eq(43L), eq(null)))
        .thenReturn(
            List.of(new CourseKc(1L, 43L, "PY43-KC-001", "变量", 1L, 1, "basic", "summary")));
    when(repository.sumMasteryTotal(7L)).thenReturn(new BigDecimal("3.2100"));
    when(repository.findPreviousMasterySum(7L)).thenReturn(Optional.of(new BigDecimal("2.7000")));
    when(repository.countSubmissionsAfter(eq(7L), any())).thenReturn(4);
    when(repository.countAcceptedSubmissionsAfter(eq(7L), any())).thenReturn(3);
    when(repository.countBugMineOpenedAfter(eq(7L), any())).thenReturn(2);
    when(repository.countBugMineResolvedAfter(eq(7L), any())).thenReturn(1);
    when(repository.countProfileRevisionsBySourceAfter(eq(7L), eq("PRACTICE_PASS"), any()))
        .thenReturn(3);
    when(repository.countProfileRevisionsBySourceAfter(eq(7L), eq("DEBUG_FIX"), any()))
        .thenReturn(1);
    when(repository.countProfileRevisionsBySourceAfter(eq(7L), eq("PRACTICE_FAIL"), any()))
        .thenReturn(1);
    when(repository.countResourceFeedbackAfter(eq(7L), any())).thenReturn(2);
    when(repository.countDistinctResourceKcAfter(eq(7L), any())).thenReturn(3);
    when(repository.findTopKcDelta(eq(7L), any(), anyInt()))
        .thenReturn(
            List.of(new KcDelta("PY43-KC-001", new BigDecimal("0.42"), new BigDecimal("0.1"))));
    when(repository.findRecentEvents(eq(7L), any(), anyInt()))
        .thenReturn(
            List.of(
                new RecentEvent(
                    "PRACTICE_AC", OffsetDateTime.now(ZoneOffset.UTC), "题目 1")));
    when(narrativeClient.narrate(any(EvaluationNarrativeRequest.class)))
        .thenAnswer(
            invocation -> {
              transactionDuringNarrativeCall.set(
                  TransactionSynchronizationManager.isActualTransactionActive());
              return successNarrative();
            });
    when(pathService.findActive(7L)).thenReturn(Optional.of(activePath(50L)));
    when(pathService.prepareDraft(7L))
        .thenAnswer(
            invocation -> {
              transactionDuringPathPlanning.set(
                  TransactionSynchronizationManager.isActualTransactionActive());
              return pathPlannerResponse();
            });
    when(pathService.persistDraft(eq(7L), any()))
        .thenAnswer(
            invocation -> {
              transactionDuringPathPersistence.set(
                  TransactionSynchronizationManager.isActualTransactionActive());
              return draftPath(60L);
            });
    when(repository.insert(eq(7L), eq(43L), any(), any(), any(), any(), eq(60L), eq(50L)))
        .thenReturn(202L);
    when(repository.findById(7L, 202L))
        .thenReturn(
            Optional.of(persisted(202L, 60L, 50L, EvaluationActedStatus.PENDING)));
    when(pathService.findById(eq(7L), eq(60L))).thenReturn(draftPath(60L));

    EvaluationDto dto = service.compute(7L, null);

    assertThat(transactionDuringNarrativeCall).isFalse();
    assertThat(transactionDuringPathPlanning).isFalse();
    assertThat(transactionDuringPathPersistence).isTrue();
    assertThat(dto.id()).isEqualTo(202L);
    assertThat(dto.draftPathId()).isEqualTo(60L);
    assertThat(dto.draftPath()).isNotNull();
    assertThat(dto.draftPath().status()).isEqualTo(LearningPathStatus.DRAFT);
    verify(pathService, times(1)).prepareDraft(7L);
    verify(pathService, times(1)).persistDraft(eq(7L), any());
    verify(repository, times(6)).insertMetric(eq(202L), any(EvaluationMetricDto.class));
  }

  @Test
  void acceptNewPathPromotesDraftAndUpdatesStatus() {
    when(repository.findById(7L, 202L))
        .thenReturn(
            Optional.of(persisted(202L, 60L, 50L, EvaluationActedStatus.PENDING)))
        .thenReturn(
            Optional.of(persisted(202L, 60L, 50L, EvaluationActedStatus.ACCEPTED)));
    when(repository.claimPendingStatus(7L, 202L, EvaluationActedStatus.ACCEPTED)).thenReturn(1);
    when(pathService.findById(eq(7L), eq(60L))).thenReturn(draftPath(60L));

    EvaluationDto accepted = service.acceptNewPath(7L, 202L);

    assertThat(accepted.actedStatus()).isEqualTo(EvaluationActedStatus.ACCEPTED);
    verify(pathService).promoteDraft(7L, 60L);
    verify(repository).claimPendingStatus(7L, 202L, EvaluationActedStatus.ACCEPTED);
  }

  @Test
  void acceptWithoutDraftThrowsConflict() {
    when(repository.findById(7L, 202L))
        .thenReturn(
            Optional.of(persisted(202L, null, 50L, EvaluationActedStatus.PENDING)));

    assertThatThrownBy(() -> service.acceptNewPath(7L, 202L))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).code())
        .isEqualTo("EVALUATION_NO_DRAFT");
    verify(pathService, never()).promoteDraft(anyLong(), anyLong());
  }

  @Test
  void concurrentAcceptLosingPendingClaimDoesNotPromoteDraft() {
    when(repository.findById(7L, 202L))
        .thenReturn(Optional.of(persisted(202L, 60L, 50L, EvaluationActedStatus.PENDING)));
    when(repository.claimPendingStatus(7L, 202L, EvaluationActedStatus.ACCEPTED)).thenReturn(0);

    assertThatThrownBy(() -> service.acceptNewPath(7L, 202L))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).code())
        .isEqualTo("EVALUATION_ALREADY_ACTED");

    verify(pathService, never()).promoteDraft(anyLong(), anyLong());
  }

  @Test
  void keepOldArchivesDraftAndMarksStatus() {
    when(repository.findById(7L, 202L))
        .thenReturn(
            Optional.of(persisted(202L, 60L, 50L, EvaluationActedStatus.PENDING)))
        .thenReturn(
            Optional.of(persisted(202L, 60L, 50L, EvaluationActedStatus.KEPT_OLD)));
    when(repository.claimPendingStatus(7L, 202L, EvaluationActedStatus.KEPT_OLD)).thenReturn(1);
    when(pathService.findById(eq(7L), eq(60L))).thenReturn(draftPath(60L));

    EvaluationDto kept = service.keepOld(7L, 202L);

    assertThat(kept.actedStatus()).isEqualTo(EvaluationActedStatus.KEPT_OLD);
    verify(pathService).archiveDraft(7L, 60L);
    verify(repository).claimPendingStatus(7L, 202L, EvaluationActedStatus.KEPT_OLD);
  }

  @Test
  void concurrentKeepLosingPendingClaimDoesNotArchiveDraft() {
    when(repository.findById(7L, 202L))
        .thenReturn(Optional.of(persisted(202L, 60L, 50L, EvaluationActedStatus.PENDING)));
    when(repository.claimPendingStatus(7L, 202L, EvaluationActedStatus.KEPT_OLD)).thenReturn(0);

    assertThatThrownBy(() -> service.keepOld(7L, 202L))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).code())
        .isEqualTo("EVALUATION_ALREADY_ACTED");

    verify(pathService, never()).archiveDraft(anyLong(), anyLong());
  }

  @Test
  void doubleAcceptIsForbidden() {
    when(repository.findById(7L, 202L))
        .thenReturn(
            Optional.of(persisted(202L, 60L, 50L, EvaluationActedStatus.ACCEPTED)));
    when(pathService.findById(eq(7L), eq(60L))).thenReturn(draftPath(60L));

    assertThatThrownBy(() -> service.acceptNewPath(7L, 202L))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).code())
        .isEqualTo("EVALUATION_ALREADY_ACTED");
  }

  @Test
  void weeklyEligibilitySkipsLearnersWithRecentReport() {
    when(repository.findActiveLearnerIdsSince(any())).thenReturn(List.of(1L, 2L, 3L));
    when(repository.existsEvaluationCreatedAfter(eq(1L), any())).thenReturn(false);
    when(repository.existsEvaluationCreatedAfter(eq(2L), any())).thenReturn(true);
    when(repository.existsEvaluationCreatedAfter(eq(3L), any())).thenReturn(false);

    List<Long> needing = service.findLearnersNeedingWeeklyEvaluation();

    // 2 号本周已有评估（自动或手动）→ 跳过，幂等不重复生成。
    assertThat(needing).containsExactly(1L, 3L);
  }

  private EvaluationDto persisted(
      Long id, Long draftId, Long activeId, EvaluationActedStatus status) {
    return new EvaluationDto(
        id,
        7L,
        43L,
        OffsetDateTime.now(ZoneOffset.UTC).minusDays(7),
        OffsetDateTime.now(ZoneOffset.UTC),
        "narrative",
        "diff",
        draftId,
        activeId,
        OffsetDateTime.now(ZoneOffset.UTC),
        status,
        List.of(),
        null);
  }

  private LearningPathDto activePath(Long id) {
    return new LearningPathDto(
        id,
        7L,
        43L,
        3,
        LearningPathStatus.ACTIVE,
        "current",
        OffsetDateTime.now(ZoneOffset.UTC).minusDays(7),
        OffsetDateTime.now(ZoneOffset.UTC),
        null,
        List.of());
  }

  private LearningPathDto draftPath(Long id) {
    return new LearningPathDto(
        id,
        7L,
        43L,
        4,
        LearningPathStatus.DRAFT,
        "draft",
        OffsetDateTime.now(ZoneOffset.UTC),
        OffsetDateTime.now(ZoneOffset.UTC).plusDays(7),
        null,
        List.of());
  }

  private LearnerProfileDto profile() {
    return new LearnerProfileDto(
        100L,
        7L,
        43L,
        "CS",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        "测试画像");
  }

  private EvaluationNarrativeResponse idleNarrative() {
    return new EvaluationNarrativeResponse(
        "近期无活动",
        List.of("先完成画像访谈"),
        "近期无数据，新路径保持系统默认。",
        new EvaluationNarrativeResponse.SafetyResult(true, List.of()),
        new EvaluationNarrativeResponse.GroundingResult(true, List.of(), List.of(), List.of()),
        "sig",
        "PARTIAL");
  }

  private EvaluationNarrativeResponse successNarrative() {
    return new EvaluationNarrativeResponse(
        "本周复盘",
        List.of("继续保持"),
        "新路径聚焦 PY43-KC-001",
        new EvaluationNarrativeResponse.SafetyResult(true, List.of()),
        new EvaluationNarrativeResponse.GroundingResult(true, List.of(), List.of(), List.of()),
        "sig",
        "SUCCESS");
  }

  private PathPlannerGraphResponse pathPlannerResponse() {
    return new PathPlannerGraphResponse(
        7L,
        43L,
        "本周聚焦 PY43-KC-001",
        List.of(),
        new PathPlannerGraphResponse.SafetyResult(true, List.of()),
        new PathPlannerGraphResponse.GroundingResult(true, List.of(), List.of()),
        "sig",
        "SUCCESS");
  }

  private static final class TestTransactionManager extends AbstractPlatformTransactionManager {

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(
        Object transaction, org.springframework.transaction.TransactionDefinition definition) {}

    @Override
    protected void doCommit(DefaultTransactionStatus status) {}

    @Override
    protected void doRollback(DefaultTransactionStatus status) {}
  }
}
