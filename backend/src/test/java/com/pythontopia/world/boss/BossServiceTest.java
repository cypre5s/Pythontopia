package com.pythontopia.world.boss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pythontopia.coursepack.domain.CourseKc;
import com.pythontopia.coursepack.repository.CoursePackRepository;
import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.learner.mastery.MasteryRepository;
import com.pythontopia.learner.mastery.MasterySnapshot;
import com.pythontopia.learner.profile.dto.BaseLevelDto;
import com.pythontopia.learner.profile.dto.EmotionPersistenceDto;
import com.pythontopia.learner.profile.dto.LearnerProfileDto;
import com.pythontopia.learner.profile.dto.PracticeCapabilityDto;
import com.pythontopia.learner.profile.service.ProfileService;
import com.pythontopia.world.boss.dto.BossAttemptRequest;
import com.pythontopia.world.boss.dto.BossEvaluateResultDto;
import com.pythontopia.world.boss.dto.BossInternalProblem;
import com.pythontopia.world.boss.dto.BossPrefetchStatusDto;
import com.pythontopia.world.boss.dto.BossProblemRequest;
import com.pythontopia.world.boss.dto.BossTestCasePayload;
import com.pythontopia.world.boss.dto.FrontierPickDto;
import com.pythontopia.world.boss.dto.FrontierSelectRequest;
import com.pythontopia.world.boss.dto.FrontierSelectResultDto;
import com.pythontopia.world.boss.review.ApprovedBossProblem;
import com.pythontopia.world.boss.review.BossProblemReviewRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.client.RestClientException;

/**
 * P3.2：Boss 出题难度据该章 KC 真实掌握度 + 置信度推断（学情驱动），而非仅按 stage。
 *
 * <p>闭环回流：boss/endless 服务端判题后须发布与 {@code SubmissionService} 同一套 {@link LearningEvent}，
 * 驱动 KC 掌握度（测）+ 8 维画像被动修订；并校验 resolveKc 章→KC 默认锚定与课程包对齐。
 */
@ExtendWith(MockitoExtension.class)
class BossServiceTest {

  private static final Long CHAPTER_ID = 11L;

  @Mock BossGraphClient graphClient;
  @Mock MasteryRepository masteryRepository;
  @Mock CoursePackRepository coursePackRepository;
  @Mock ProfileService profileService;
  @Mock EndlessPregenService pregenService;
  @Mock BossProblemReviewRepository reviewRepository;
  @Mock ExpeditionRunService expeditionRunService;
  @Mock ExpeditionScoreService expeditionScoreService;
  @Mock ApplicationEventPublisher eventPublisher;
  private BossService service;

  @BeforeEach
  void setUp() {
    service =
        new BossService(
            graphClient,
            masteryRepository,
            coursePackRepository,
            profileService,
            pregenService,
            reviewRepository,
            expeditionRunService,
            expeditionScoreService,
            eventPublisher);
  }

  @Test
  @DisplayName("学生取题命中全局 approved bank 时不调用 Graph，并为该学生保存判题 payload")
  void approvedBankIsPreferredBeforeOnDemandGraphGeneration() {
    BossInternalProblem approved =
        new BossInternalProblem(
            "approved-33-v2",
            1,
            1,
            2,
            "PY43-KC-001",
            "approved statement",
            List.of("hint"),
            "2 3",
            "5",
            1,
            true,
            true,
            List.of(new BossTestCasePayload("2 3", "5", false)),
            "print(5)");
    when(reviewRepository.findApproved(1, 1, "PY43-KC-001", "debug", 2))
        .thenReturn(Optional.of(new ApprovedBossProblem(33L, 2, approved)));

    var dto =
        service.fetchProblem(
            1,
            new BossProblemRequest(1, null, 2, "PY43-KC-001", "debug", null, null, null),
            7L);

    assertThat(dto.problemId()).startsWith("issue-").isNotEqualTo("approved-33-v2");
    assertThat(dto.statement()).isEqualTo("approved statement");
    verify(pregenService)
        .persistConsumed(
            eq(7L),
            eq(1),
            eq("PY43-KC-001"),
            eq("debug"),
            org.mockito.ArgumentMatchers.isNull(),
            eq(2),
            org.mockito.ArgumentMatchers.argThat(p -> p.problemId().equals(dto.problemId())),
            org.mockito.ArgumentMatchers.isNull());
    verifyNoInteractions(graphClient);
  }

  @Test
  @DisplayName("学生已有 READY 个性化预取题时先消费该题，再查询全局 approved bank")
  void readyPersonalizedProblemTakesPriorityOverApprovedBank() {
    BossInternalProblem ready =
        new BossInternalProblem(
            "ready-for-user-7",
            1,
            1,
            2,
            "PY43-KC-001",
            "personalized ready statement",
            List.of(),
            "2 3",
            "5",
            1,
            true,
            true,
            List.of(new BossTestCasePayload("2 3", "5", false)),
            "print(5)");
    when(pregenService.claimReady(7L, 1, "PY43-KC-001", 2, "debug", null, null))
        .thenReturn(Optional.of(ready));

    var dto =
        service.fetchProblem(
            1,
            new BossProblemRequest(1, null, 2, "PY43-KC-001", "debug", null, null, null),
            7L);

    assertThat(dto.problemId()).isEqualTo("ready-for-user-7");
    verifyNoInteractions(reviewRepository, graphClient);
  }

  @Test
  @DisplayName("同一图生成 ID 不会跨用户或同用户复用隐藏测试")
  void generatedProblemsReceiveUniqueUserScopedIds() {
    when(graphClient.requestProblem(anyLong(), anyInt(), anyInt(), anyString(), anyInt(), any(), any(), any()))
        .thenReturn(
            problemWithInputAndKc("private-user-7", "FAKE-KC"),
            problemWithInput("private-user-8"),
            problemWithInput("private-user-8"));
    when(graphClient.evaluateAttempt(anyString(), anyString(), anyList(), anyInt()))
        .thenReturn(new BossEvaluateResultDto("ignored", true, 1, 1, null, null, List.of()));
    BossProblemRequest request = new BossProblemRequest(1, null, 1, null, null, null, null, null);

    var user7First = service.fetchProblem(1, request, 7L);
    var user7Second = service.fetchProblem(1, request, 7L);
    var user8 = service.fetchProblem(1, request, 8L);

    assertThat(user7First.problemId()).isNotEqualTo(user7Second.problemId());
    assertThat(user7First.problemId()).isNotEqualTo(user8.problemId());
    assertThat(user7First.kcCode()).isEqualTo("PY43-KC-001");

    service.evaluateAttempt(
        new BossAttemptRequest(user7First.problemId(), 1, 1, "print(1)", 1), 7L);
    service.evaluateAttempt(
        new BossAttemptRequest(user7Second.problemId(), 1, 1, "print(1)", 1), 7L);
    service.evaluateAttempt(new BossAttemptRequest(user8.problemId(), 1, 1, "print(1)", 1), 8L);

    ArgumentCaptor<List<BossTestCasePayload>> cases = ArgumentCaptor.forClass(List.class);
    verify(graphClient, org.mockito.Mockito.times(3))
        .evaluateAttempt(anyString(), anyString(), cases.capture(), anyInt());
    assertThat(cases.getAllValues())
        .extracting(testCases -> testCases.get(0).input())
        .containsExactly("private-user-7", "private-user-8", "private-user-8");
  }

  @Test
  @DisplayName("其他用户不能用猜到的题目 ID 读取已发放题目的隐藏测试")
  void generatedProblemCannotBeEvaluatedByAnotherUser() {
    when(graphClient.requestProblem(anyLong(), anyInt(), anyInt(), anyString(), anyInt(), any(), any(), any()))
        .thenReturn(problemWithInput("private-user-7"));
    var problem =
        service.fetchProblem(
            1, new BossProblemRequest(1, null, 1, null, null, null, null, null), 7L);

    service.evaluateAttempt(
        new BossAttemptRequest(problem.problemId(), 1, 1, "print(1)", 1), 8L);

    verify(graphClient, never()).evaluateAttempt(anyString(), anyString(), anyList(), anyInt());
    verify(pregenService).findByProblemId(8L, problem.problemId());
    verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("远征题绑定活跃 run，奖励由服务端判题结果记录")
  void verifiedAttemptIsBoundToActiveRun() {
    String runId = "123e4567-e89b-12d3-a456-426614174000";
    when(expeditionRunService.isActiveRun(7L, runId)).thenReturn(true);
    when(graphClient.requestProblem(anyLong(), anyInt(), anyInt(), anyString(), anyInt(), any(), any(), any()))
        .thenReturn(problemWithInput("server-owned"));
    when(graphClient.evaluateAttempt(anyString(), anyString(), anyList(), anyInt()))
        .thenReturn(new BossEvaluateResultDto("ignored", true, 1, 1, null, null, List.of()));
    when(pregenService.findExpeditionRunId(eq(7L), anyString())).thenReturn(Optional.of(runId));

    BossProblemRequest request = new BossProblemRequest(1, null, 1, null, null, null, null, runId);
    var problem = service.fetchProblem(1, request, 7L);
    service.evaluateAttempt(new BossAttemptRequest(problem.problemId(), 1, 1, "print(1)", 1), 7L);

    verify(pregenService)
        .persistConsumed(
            eq(7L),
            eq(1),
            eq("PY43-KC-001"),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.isNull(),
            eq(1),
            org.mockito.ArgumentMatchers.argThat(p -> p.problemId().equals(problem.problemId())),
            eq(runId));
    verify(expeditionScoreService)
        .recordVerifiedAttempt(7L, runId, problem.problemId(), true, 100);
  }

  @Test
  @DisplayName("已失效的远征 run 不得发放可计分题目")
  void staleExpeditionRunCannotRequestProblem() {
    String staleRunId = "123e4567-e89b-12d3-a456-426614174001";
    assertThatThrownBy(
            () ->
                service.fetchProblem(
                    1,
                    new BossProblemRequest(1, null, 1, null, null, null, null, staleRunId),
                    7L))
        .isInstanceOf(com.pythontopia.common.error.BusinessException.class)
        .hasMessageContaining("远征存档");
    verifyNoInteractions(graphClient, pregenService, reviewRepository);
  }

  @Test
  @DisplayName("selectFrontier 使用真实练习事件时间，忽略 DECAY 推进的 last_event_at")
  void selectFrontierPassesPersistedLastPracticedToGraph() {
    OffsetDateTime lastPracticed = OffsetDateTime.parse("2026-05-01T08:30:00Z");
    when(masteryRepository.findLastPracticedAtByUser(7L))
        .thenReturn(Map.of("PY43-KC-001", lastPracticed));
    when(masteryRepository.findAll(7L))
        .thenReturn(
            List.of(
                new MasterySnapshot(
                    1L,
                    7L,
                    "PY43-KC-001",
                    new BigDecimal("0.82"),
                    new BigDecimal("0.80"),
                    lastPracticed.plusDays(10),
                    lastPracticed.plusDays(10))));
    when(graphClient.selectFrontier(
            any(), any(), any(), any(), any(), any(), anyInt(), any()))
        .thenReturn(
            new FrontierSelectResultDto(
                List.of(new FrontierPickDto("PY43-KC-001", 1, 4, "最近发展区"))));

    service.selectFrontier(
        7L,
        new FrontierSelectRequest(null, List.of(), 1, List.of(), 4));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Double>> confidenceCaptor = ArgumentCaptor.forClass(Map.class);
    verify(graphClient)
        .selectFrontier(
            any(),
            confidenceCaptor.capture(),
            any(),
            any(),
            any(),
            any(),
            anyInt(),
            captor.capture());
    assertThat(captor.getValue()).containsEntry("PY43-KC-001", "2026-05-01T08:30:00Z");
    assertThat(confidenceCaptor.getValue()).containsEntry("PY43-KC-001", 0.8);
  }

  @Test
  @DisplayName("纯函数：低掌握→易，高掌握+高置信→难，低置信降档")
  void bossDifficultyPureFunction() {
    assertThat(BossService.bossDifficulty(0.10, 0.5)).isEqualTo(1);
    assertThat(BossService.bossDifficulty(0.90, 0.85)).isEqualTo(5);
    // 临界 0.5（base 3）低置信 → 降一档 2。
    assertThat(BossService.bossDifficulty(0.50, 0.20)).isEqualTo(2);
    // 0.78（base 4，逼近上沿）高置信 → 升一档 5。
    assertThat(BossService.bossDifficulty(0.78, 0.80)).isEqualTo(5);
  }

  @Test
  @DisplayName("低掌握章节 → 在纵深主轴上温和降一档")
  void lowMasteryYieldsEasierBossDifficulty() {
    stubChapterKcs();
    when(masteryRepository.findAll(7L))
        .thenReturn(
            List.of(
                snapshot("PY43-KC-001", "0.10", "0.50"),
                snapshot("PY43-KC-002", "0.15", "0.50")));
    when(graphClient.requestProblem(
            anyLong(), anyInt(), anyInt(), anyString(), anyInt(), any(), any(), any()))
        .thenReturn(internalProblem());

    // stage=4：旧逻辑会出难度 4；学情驱动应据低掌握出更易的题。
    service.fetchProblem(
        1, new BossProblemRequest(4, null, null, null, null, null, null, null), 7L);

    int difficulty = capturedDifficulty();
    assertThat(difficulty).isEqualTo(3);
    assertThat(difficulty).isLessThan(4);
  }

  @Test
  @DisplayName("高掌握+高置信章节 → 在纵深主轴上升一档")
  void highMasteryHighConfidenceYieldsHarderBossDifficulty() {
    stubChapterKcs();
    when(masteryRepository.findAll(7L))
        .thenReturn(
            List.of(
                snapshot("PY43-KC-001", "0.90", "0.85"),
                snapshot("PY43-KC-002", "0.92", "0.80")));
    when(graphClient.requestProblem(
            anyLong(), anyInt(), anyInt(), anyString(), anyInt(), any(), any(), any()))
        .thenReturn(internalProblem());

    service.fetchProblem(
        1, new BossProblemRequest(2, null, null, null, null, null, null, null), 7L);

    assertThat(capturedDifficulty()).isEqualTo(3);
  }

  @Test
  @DisplayName("前端 difficulty 仅表达纵深，不能绕过服务端画像")
  void explicitDifficultyIsAdaptedByServerProfile() {
    when(coursePackRepository.findKcs(eq(43L), eq(null))).thenReturn(List.of());
    when(profileService.findProfile(7L)).thenReturn(Optional.of(beginnerProfile()));
    when(graphClient.requestProblem(
            anyLong(), anyInt(), anyInt(), anyString(), anyInt(), any(), any(), any()))
        .thenReturn(internalProblem());

    service.fetchProblem(
        1, new BossProblemRequest(4, null, 5, null, null, null, null, null), 7L);

    assertThat(capturedDifficulty()).isEqualTo(4);
  }

  @Test
  @DisplayName("画像自适应仍保持纵深难度单调递进")
  void profileAdaptiveDifficultyRemainsMonotonicByDepth() {
    when(coursePackRepository.findKcs(eq(43L), eq(null))).thenReturn(List.of());
    when(profileService.findProfile(7L)).thenReturn(Optional.of(beginnerProfile()));

    assertThat(
            java.util.stream.IntStream.rangeClosed(1, 5)
                .map(depth -> service.computeTargetDifficulty(7L, "PY43-KC-001", 1, depth))
                .boxed()
                .toList())
        .containsExactly(1, 1, 2, 3, 4);
  }

  @Test
  @DisplayName("预加载轮询使用与 POST 相同的服务端画像合成难度")
  void prefetchStatusUsesAuthoritativeDifficultyDimensions() {
    when(coursePackRepository.findKcs(eq(43L), eq(null))).thenReturn(List.of());
    when(profileService.findProfile(7L)).thenReturn(Optional.of(beginnerProfile()));
    when(pregenService.getPrefetchStatus(7L, 1, "PY43-KC-001", 4, "debug", "1-5"))
        .thenReturn(BossPrefetchStatusDto.idle());

    service.prefetchStatus(1, "PY43-KC-001", 5, "debug", "1-5", 7L);

    verify(pregenService).getPrefetchStatus(7L, 1, "PY43-KC-001", 4, "debug", "1-5");
  }

  @Test
  @DisplayName("attempt 通过 → 发布 PracticeSucceeded（闭环回流 KC 掌握度 / 画像）")
  void passingAttemptPublishesPracticeSucceeded() {
    when(pregenService.findByProblemId(7L, "p1")).thenReturn(Optional.of(problemWithTests(4)));
    when(graphClient.evaluateAttempt(eq("p1"), anyString(), anyList(), anyInt()))
        .thenReturn(new BossEvaluateResultDto("p1", true, 1, 1, null, null, List.of()));

    service.evaluateAttempt(new BossAttemptRequest("p1", 1, 1, "print(5)", 10), 7L);

    ArgumentCaptor<LearningEvent.PracticeSucceeded> captor =
        ArgumentCaptor.forClass(LearningEvent.PracticeSucceeded.class);
    verify(eventPublisher).publishEvent(captor.capture());
    LearningEvent.PracticeSucceeded event = captor.getValue();
    assertThat(event.userId()).isEqualTo(7L);
    assertThat(event.kcCode()).isEqualTo("PY43-KC-001");
    assertThat(event.problemId()).isNull(); // boss 题无 course_problem 数字主键
    assertThat(event.difficulty()).isEqualTo("hard"); // difficulty 4 → hard
  }

  @Test
  @DisplayName("不能用可猜测的全局审核题 ID 绕过用户题目签发")
  void approvedAttemptRejectsGlobalReviewBankIdNotIssuedToUser() {
    when(pregenService.findByProblemId(7L, "approved-33-v2"))
        .thenReturn(Optional.empty());
    var result =
        service.evaluateAttempt(
            new BossAttemptRequest("approved-33-v2", 1, 1, "print(5)", 10), 7L);

    assertThat(result.passed()).isFalse();
    verify(graphClient, never()).evaluateAttempt(anyString(), anyString(), anyList(), anyInt());
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  @DisplayName("attempt 未通过 → 发布 PracticeFailed（verdict=WA）")
  void failingAttemptPublishesPracticeFailed() {
    when(pregenService.findByProblemId(7L, "p1")).thenReturn(Optional.of(problemWithTests(2)));
    when(graphClient.evaluateAttempt(eq("p1"), anyString(), anyList(), anyInt()))
        .thenReturn(new BossEvaluateResultDto("p1", false, 0, 1, "hint", null, List.of(0)));

    service.evaluateAttempt(new BossAttemptRequest("p1", 1, 1, "print(0)", 10), 7L);

    ArgumentCaptor<LearningEvent.PracticeFailed> captor =
        ArgumentCaptor.forClass(LearningEvent.PracticeFailed.class);
    verify(eventPublisher).publishEvent(captor.capture());
    LearningEvent.PracticeFailed event = captor.getValue();
    assertThat(event.kcCode()).isEqualTo("PY43-KC-001");
    assertThat(event.failureKind()).isEqualTo("WA");
    assertThat(event.difficulty()).isEqualTo("easy"); // difficulty 2 → easy
  }

  @Test
  @DisplayName("attempt 运行错误 → 发布 PracticeFailed（verdict=RE）")
  void runtimeErrorAttemptPublishesReVerdict() {
    when(pregenService.findByProblemId(7L, "p1")).thenReturn(Optional.of(problemWithTests(3)));
    when(graphClient.evaluateAttempt(eq("p1"), anyString(), anyList(), anyInt()))
        .thenReturn(new BossEvaluateResultDto("p1", false, 0, 1, null, "NameError: x", List.of()));

    service.evaluateAttempt(new BossAttemptRequest("p1", 1, 1, "print(x)", 10), 7L);

    ArgumentCaptor<LearningEvent.PracticeFailed> captor =
        ArgumentCaptor.forClass(LearningEvent.PracticeFailed.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().failureKind()).isEqualTo("RE");
  }

  @Test
  @DisplayName("题目缓存缺失（无判定）→ 不发任何学习事件")
  void missingProblemPublishesNothing() {
    when(pregenService.findByProblemId(7L, "missing")).thenReturn(Optional.empty());

    service.evaluateAttempt(new BossAttemptRequest("missing", 1, 1, "print(1)", 10), 7L);

    verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("graph 评测不可用（无判定）→ 不发任何学习事件")
  void graphUnavailablePublishesNothing() {
    when(pregenService.findByProblemId(7L, "p1")).thenReturn(Optional.of(problemWithTests(3)));
    when(graphClient.evaluateAttempt(eq("p1"), anyString(), anyList(), anyInt()))
        .thenThrow(new RestClientException("graph down"));

    service.evaluateAttempt(new BossAttemptRequest("p1", 1, 1, "print(1)", 10), 7L);

    verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("匿名用户（userId=null）→ 不发学习事件（无法归因）")
  void anonymousUserPublishesNothing() {
    when(pregenService.findByProblemId(any(), eq("p1")))
        .thenReturn(Optional.of(problemWithTests(3)));
    when(graphClient.evaluateAttempt(eq("p1"), anyString(), anyList(), anyInt()))
        .thenReturn(new BossEvaluateResultDto("p1", true, 1, 1, null, null, List.of()));

    service.evaluateAttempt(new BossAttemptRequest("p1", 1, 1, "print(5)", 10), null);

    verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("resolveKc 章→默认 KC 与 course-pack-43 / ChapterCatalog 对齐（修复旧错位）")
  void resolveKcMatchesCoursePack() {
    assertThat(service.resolveKc(1, null)).isEqualTo("PY43-KC-001");
    assertThat(service.resolveKc(2, null)).isEqualTo("PY43-KC-008");
    assertThat(service.resolveKc(3, null)).isEqualTo("PY43-KC-017");
    assertThat(service.resolveKc(4, null)).isEqualTo("PY43-KC-031");
    assertThat(service.resolveKc(5, null)).isEqualTo("PY43-KC-039");
    assertThat(service.resolveKc(6, null)).isEqualTo("PY43-KC-048");
    assertThat(service.resolveKc(7, null)).isEqualTo("PY43-KC-054");
    assertThat(service.resolveKc(3, "PY43-KC-017")).isEqualTo("PY43-KC-017");
    assertThatThrownBy(() -> service.resolveKc(3, "PY43-KC-099"))
        .isInstanceOf(com.pythontopia.common.error.BusinessException.class)
        .hasMessageContaining("KC");
    assertThatThrownBy(() -> service.resolveKc(3, "NOT-A-COURSE-KC"))
        .isInstanceOf(com.pythontopia.common.error.BusinessException.class);
  }

  @Test
  @DisplayName("difficultyBand：1..2→easy，3→medium，4..5→hard")
  void difficultyBandMapping() {
    assertThat(BossService.difficultyBand(1)).isEqualTo("easy");
    assertThat(BossService.difficultyBand(2)).isEqualTo("easy");
    assertThat(BossService.difficultyBand(3)).isEqualTo("medium");
    assertThat(BossService.difficultyBand(4)).isEqualTo("hard");
    assertThat(BossService.difficultyBand(5)).isEqualTo("hard");
  }

  private void stubChapterKcs() {
    lenient()
        .when(coursePackRepository.findKcs(eq(43L), eq(null)))
        .thenReturn(
            List.of(
                new CourseKc(1L, 43L, "PY43-KC-001", "输入输出", CHAPTER_ID, 1, "basic", "s"),
                new CourseKc(2L, 43L, "PY43-KC-002", "变量", CHAPTER_ID, 1, "basic", "s")));
  }

  private int capturedDifficulty() {
    ArgumentCaptor<Integer> difficultyCaptor = ArgumentCaptor.forClass(Integer.class);
    verify(graphClient)
        .requestProblem(
            anyLong(),
            anyInt(),
            anyInt(),
            anyString(),
            difficultyCaptor.capture(),
            any(),
            any(),
            any());
    return difficultyCaptor.getValue();
  }

  private static MasterySnapshot snapshot(String kc, String score, String confidence) {
    return new MasterySnapshot(
        1L, 7L, kc, new BigDecimal(score), new BigDecimal(confidence), null, null);
  }

  private static BossInternalProblem internalProblem() {
    return new BossInternalProblem(
        "p1", 1, 1, 1, "PY43-KC-001", "statement", List.of(), "in", "out", 1, true, true,
        List.of(), "");
  }

  private static LearnerProfileDto beginnerProfile() {
    return new LearnerProfileDto(
        1L,
        7L,
        43L,
        "non-cs",
        new BaseLevelDto(0, List.of(), 1),
        null,
        null,
        null,
        null,
        new PracticeCapabilityDto(4.0, 0.1),
        new EmotionPersistenceDto(0.2, "frustrated"),
        "beginner");
  }

  /** 带 hidden testCases 的内部题：evaluateAttempt 才能越过「空 testCases」兜底进入真实判题路径。 */
  private static BossInternalProblem problemWithTests(int difficulty) {
    return new BossInternalProblem(
        "p1", 1, 1, difficulty, "PY43-KC-001", "statement", List.of(), "2 3", "5", 1, true, true,
        List.of(new BossTestCasePayload("2 3", "5", false)), "ref");
  }

  private static BossInternalProblem problemWithInput(String input) {
    return problemWithInputAndKc(input, "PY43-KC-001");
  }

  private static BossInternalProblem problemWithInputAndKc(String input, String kcCode) {
    return new BossInternalProblem(
        "same-graph-id", 1, 1, 1, kcCode, "statement", List.of(), "", "", 1,
        true, true, List.of(new BossTestCasePayload(input, "expected", false)), "ref");
  }
}
