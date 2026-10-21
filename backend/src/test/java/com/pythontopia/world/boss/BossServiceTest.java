package com.pythontopia.world.boss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.learner.profile.dto.LearnerProfileSignals;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import com.pythontopia.learner.twin.LearnerTwinService;
import com.pythontopia.mainline.practice.MainlinePracticeQuestion;
import com.pythontopia.mainline.practice.MainlinePracticeRepository;
import com.pythontopia.mainline.practice.PracticeQuestionType;
import com.pythontopia.mainline.test.ChapterProblemBankRepository;
import com.pythontopia.mainline.test.ChapterProblemBankRepository.CodingCase;
import com.pythontopia.mainline.test.ChapterProblemBankRepository.PreferredCodingQuestion;
import com.pythontopia.submission.dto.Verdict;
import com.pythontopia.submission.judge.JudgeCaseEvaluator;
import com.pythontopia.submission.judge.JudgeWorkerPool;
import com.pythontopia.submission.judge.StaticCodeChecker;
import com.pythontopia.world.boss.dto.BossAttemptRequest;
import com.pythontopia.world.boss.dto.BossEvaluateResultDto;
import com.pythontopia.world.boss.dto.BossInternalProblem;
import com.pythontopia.world.boss.dto.BossPrefetchStatusDto;
import com.pythontopia.world.boss.dto.BossProblemRequest;
import com.pythontopia.world.boss.dto.BossProblemRequest.ProfileSignalsPayload;
import com.pythontopia.world.boss.dto.BossQuestionType;
import com.pythontopia.world.boss.dto.BossTestCasePayload;
import com.pythontopia.world.boss.dto.FrontierPickDto;
import com.pythontopia.world.boss.dto.FrontierSelectRequest;
import com.pythontopia.world.boss.dto.FrontierSelectResultDto;
import com.pythontopia.world.boss.review.ApprovedBossProblem;
import com.pythontopia.world.boss.review.BossProblemReviewRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;

/**
 * Boss shares the Twin's deterministic practice policy; verified expedition results feed back into
 * learning.
 */
@ExtendWith(MockitoExtension.class)
class BossServiceTest {

  @Mock BossGraphClient graphClient;
  @Mock PassiveProfileService passiveProfileService;
  @Mock LearnerTwinService learnerTwinService;
  @Mock MainlinePracticeRepository mainlinePracticeRepository;
  @Mock ChapterProblemBankRepository chapterProblemBankRepository;
  @Mock JudgeWorkerPool judgeWorkerPool;
  @Mock StaticCodeChecker staticCodeChecker;
  @Mock JudgeCaseEvaluator judgeCaseEvaluator;
  @Mock EndlessPregenService pregenService;
  @Mock BossProblemReviewRepository reviewRepository;
  @Mock ExpeditionRunService expeditionRunService;
  @Mock BossAttemptIdempotencyService attemptIdempotencyService;
  @Mock ExpeditionAttemptCompletionService expeditionAttemptCompletionService;
  @Mock ApplicationEventPublisher eventPublisher;
  private BossService service;

  @BeforeEach
  void setUp() {
    service =
        new BossService(
            graphClient,
            passiveProfileService,
            learnerTwinService,
            mainlinePracticeRepository,
            chapterProblemBankRepository,
            judgeWorkerPool,
            staticCodeChecker,
            judgeCaseEvaluator,
            pregenService,
            reviewRepository,
            expeditionRunService,
            attemptIdempotencyService,
            expeditionAttemptCompletionService,
            eventPublisher);
    lenient()
        .when(
            expeditionAttemptCompletionService.complete(
                anyLong(),
                anyString(),
                anyString(),
                org.mockito.ArgumentMatchers.nullable(Integer.class),
                anyString(),
                anyString(),
                anyBoolean(),
                anyInt(),
                org.mockito.ArgumentMatchers.nullable(UUID.class),
                org.mockito.ArgumentMatchers.nullable(BossAttemptIdempotencyService.Claim.class),
                any(com.pythontopia.world.boss.dto.BossAttemptResultDto.class),
                org.mockito.ArgumentMatchers.nullable(LearningEvent.class)))
        .thenAnswer(
            call ->
                new ExpeditionAttemptCompletionService.Completion(
                    true, call.getArgument(10)));
    lenient()
        .when(passiveProfileService.getPersonalizationSignals(anyLong()))
        .thenReturn(LearnerProfileSignals.empty());
    lenient()
        .when(learnerTwinService.recommendedDifficulty(anyLong(), anyString()))
        .thenReturn(null);
    lenient().when(reviewRepository.isApproved(any())).thenReturn(true);
    lenient()
        .when(
            expeditionRunService.validateProblemRequest(
                anyLong(), anyString(), anyString(), anyInt(), anyBoolean(), anyString()))
        .thenReturn(null);
    lenient()
        .when(attemptIdempotencyService.claim(anyLong(), any(), any()))
        .thenAnswer(
            invocation ->
                new BossAttemptIdempotencyService.Claim(UUID.randomUUID(), null, "a".repeat(64)));
    lenient()
        .when(attemptIdempotencyService.complete(anyLong(), any(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              LearningEvent event = invocation.getArgument(4);
              if (event != null) eventPublisher.publishEvent(event);
              return invocation.getArgument(3);
            });
  }

  @Test
  @DisplayName("无同 KC 编程题时 Boss 使用客观题并把通过结果写回远征和学习事件")
  void bossFallsBackToObjectiveQuestionAndPublishesVerifiedOutcome() {
    String runId = UUID.randomUUID().toString();
    String nodeId = "L9-0";
    String kcCode = "PY43-KC-008";
    var sourceQuestion =
        new MainlinePracticeQuestion(
            15L,
            2,
            1,
            3,
            kcCode,
            PracticeQuestionType.JUDGE,
            "在 Python 中可以使用 print() 函数把内容输出到屏幕。",
            List.of(),
            List.of("正确"),
            0,
            "print() 是 Python 的标准输出内置函数。",
            "通识题（非课件原题）");
    when(expeditionRunService.validateProblemRequest(7L, runId, nodeId, 2, false, kcCode))
        .thenReturn(1);
    when(expeditionRunService.findIssuedQuestionFingerprints(7L, runId)).thenReturn(Set.of());
    when(chapterProblemBankRepository.findByKcCodes(List.of(kcCode))).thenReturn(List.of());
    when(mainlinePracticeRepository.findForBossFallback(2, kcCode))
        .thenReturn(List.of(sourceQuestion));

    var problem =
        service.fetchProblem(
            2,
            new BossProblemRequest(2, null, null, kcCode, "dual_track", null, null, runId, nodeId),
            7L);

    assertThat(problem.questionType()).isEqualTo(BossQuestionType.JUDGE);
    assertThat(problem.statement()).contains("print()");
    assertThat(problem.options()).isEmpty();
    assertThat(problem.toString()).doesNotContain("正确");
    verifyNoInteractions(graphClient);

    BossInternalProblem issued =
        new BossInternalProblem(
                "boss-objective-15",
                2,
                problem.stage(),
                problem.difficulty(),
                kcCode,
                sourceQuestion.stem(),
                List.of(),
                "",
                "",
                1,
                true,
                false,
                List.of(),
                "",
                BossQuestionType.JUDGE,
                List.of(),
                sourceQuestion.answers(),
                0)
            .issueAs(problem.problemId(), kcCode);
    when(pregenService.findExpeditionBinding(7L, problem.problemId()))
        .thenReturn(
            Optional.of(
                new EndlessPregenService.ExpeditionProblemBinding(
                    runId,
                    nodeId,
                    2,
                    problem.stage(),
                    kcCode,
                    BossService.questionFingerprint(issued),
                    1)));
    when(expeditionRunService.isActiveRun(7L, runId)).thenReturn(true);
    when(expeditionScoreService.recordVerifiedAttempt(
            anyLong(),
            anyString(),
            anyString(),
            anyString(),
            anyBoolean(),
            anyInt(),
            any(LearningEvent.class)))
        .thenReturn(new ExpeditionScoreService.VerifiedAttemptResult(true, true));

    var result =
        service.evaluateAttempt(
            new BossAttemptRequest(
                problem.problemId(),
                2,
                problem.stage(),
                null,
                12,
                UUID.randomUUID(),
                List.of("正确")),
            7L);

    assertThat(result.passed()).isTrue();
    assertThat(result.passedTestCases()).isEqualTo(1);
    assertThat(result.feedback()).contains("服务端已确认");
    verify(expeditionScoreService)
        .recordVerifiedAttempt(
            eq(7L),
            eq(runId),
            eq(problem.problemId()),
            anyString(),
            eq(true),
            eq(100),
            any(LearningEvent.class));
    verifyNoInteractions(eventPublisher);
    verify(graphClient, never()).evaluateAttempt(anyString(), anyString(), anyList(), anyInt());
  }

  @Test
  @DisplayName("远征填空题由服务端归一化判分并回流 Twin")
  void fillObjectiveAttemptUsesServerMatcherAndPublishesVerifiedOutcome() {
    BossInternalProblem fillProblem =
        new BossInternalProblem(
                "fill-objective",
                1,
                1,
                2,
                "PY43-KC-001",
                "填写 print 与 True",
                List.of(),
                "",
                "",
                1,
                true,
                false,
                List.of(),
                "",
                BossQuestionType.FILL,
                List.of(),
                List.of("print", "True"),
                2)
            .issueAs("fill-objective-user-7", "PY43-KC-001");
    when(pregenService.findByProblemId(7L, fillProblem.problemId()))
        .thenReturn(Optional.of(fillProblem));

    var result =
        service.evaluateAttempt(
            new BossAttemptRequest(
                fillProblem.problemId(),
                1,
                1,
                null,
                9,
                UUID.randomUUID(),
                List.of(" \u201CＰＲＩＮＴ\u201D ", "ＴＲＵＥ")),
            7L);

    assertThat(result.passed()).isTrue();
    assertThat(result.passedTestCases()).isEqualTo(1);
    assertThat(result.feedback()).contains("服务端已确认");
    verify(graphClient, never()).evaluateAttempt(anyString(), anyString(), anyList(), anyInt());
    org.mockito.ArgumentCaptor<LearningEvent.PracticeSucceeded> eventCaptor =
        org.mockito.ArgumentCaptor.forClass(LearningEvent.PracticeSucceeded.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().kcCode()).isEqualTo("PY43-KC-001");
  }

  @Test
  @DisplayName("普通远征节点为无编程题 KC 复用同 KC 客观题")
  void expeditionBattleFallsBackToObjectiveQuestionWithoutGraph() {
    String runId = UUID.randomUUID().toString();
    String nodeId = "L0-0";
    String kcCode = "PY43-KC-001";
    when(expeditionRunService.validateProblemRequest(7L, runId, nodeId, 1, false, kcCode))
        .thenReturn(null);
    when(expeditionRunService.findIssuedQuestionFingerprints(7L, runId)).thenReturn(Set.of());
    when(chapterProblemBankRepository.findByKcCodes(List.of(kcCode))).thenReturn(List.of());
    when(mainlinePracticeRepository.findForBossFallback(1, kcCode))
        .thenReturn(
            List.of(
                new MainlinePracticeQuestion(
                    1001L,
                    1,
                    3,
                    110,
                    kcCode,
                    PracticeQuestionType.CHOICE,
                    "计算工具发展到电子计算机阶段后，主要实现了什么？",
                    List.of("自动化计算", "人工算盘"),
                    List.of("自动化计算"),
                    0,
                    "依据课程材料。",
                    "课件页 1")));

    var problem =
        service.fetchProblem(
            1,
            new BossProblemRequest(1, null, null, kcCode, "dual_track", null, null, runId, nodeId),
            7L);

    assertThat(problem.questionType()).isEqualTo(BossQuestionType.CHOICE);
    assertThat(problem.toString()).doesNotContain("answers=");
    verifyNoInteractions(graphClient);
  }

  @Test
  @DisplayName("无课程编程题的远征节点优先发放同 KC 客观题而非预生成代码题")
  void expeditionPrefersObjectiveQuestionOverOpenCodePrefetch() {
    String runId = UUID.randomUUID().toString();
    String nodeId = "L0-0";
    String kcCode = "PY43-KC-001";
    var prefetchedCode =
        new BossInternalProblem(
            "prefetched-code",
            1,
            1,
            1,
            kcCode,
            "不适合编程练习的概念题",
            List.of(),
            "",
            "",
            1,
            true,
            true,
            List.of(new BossTestCasePayload("", "1", false)),
            "print(1)",
            BossQuestionType.CODE,
            List.of(),
            List.of(),
            0);
    when(expeditionRunService.validateProblemRequest(7L, runId, nodeId, 1, false, kcCode))
        .thenReturn(null);
    when(expeditionRunService.findIssuedQuestionFingerprints(7L, runId))
        .thenReturn(Set.of(BossService.questionFingerprint(prefetchedCode)));
    when(chapterProblemBankRepository.findByKcCodes(List.of(kcCode))).thenReturn(List.of());
    when(mainlinePracticeRepository.findForBossFallback(1, kcCode))
        .thenReturn(
            List.of(
                new MainlinePracticeQuestion(
                    1001L,
                    1,
                    3,
                    110,
                    kcCode,
                    PracticeQuestionType.JUDGE,
                    "中国自主研制超级计算机体现了计算机领域的发展成就。",
                    List.of(),
                    List.of("正确"),
                    0,
                    "依据课程材料。",
                    "课件页 10708")));
    var resumedObjective =
        new BossInternalProblem(
            "issue-saved-objective",
            1,
            1,
            1,
            kcCode,
            "中国自主研制超级计算机体现了计算机领域的发展成就。",
            List.of(),
            "",
            "",
            1,
            true,
            false,
            List.of(),
            "",
            BossQuestionType.JUDGE,
            List.of(),
            List.of("正确"),
            0);
    when(pregenService.findOpenExpeditionProblem(7L, runId, nodeId, null))
        .thenReturn(Optional.of(prefetchedCode), Optional.of(resumedObjective));
    when(reviewRepository.isApproved(prefetchedCode)).thenReturn(true);

    var problem =
        service.fetchProblem(
            1,
            new BossProblemRequest(1, null, null, kcCode, "dual_track", null, null, runId, nodeId),
            7L);

    assertThat(problem.questionType()).isEqualTo(BossQuestionType.JUDGE);
    assertThat(problem.statement()).contains("中国自主研制超级计算机");
    var resumed =
        service.fetchProblem(
            1,
            new BossProblemRequest(1, null, null, kcCode, "dual_track", null, null, runId, nodeId),
            7L);
    assertThat(resumed.problemId()).isEqualTo("issue-saved-objective");
    assertThat(resumed.questionType()).isEqualTo(BossQuestionType.JUDGE);
    verifyNoInteractions(graphClient);
  }

  @Test
  @DisplayName("远征客观题池用尽时不回退到 READY 编程题")
  void expeditionFailsClosedInsteadOfClaimingReadyCodeWhenObjectivePoolIsExhausted() {
    String runId = UUID.randomUUID().toString();
    String nodeId = "L0-0";
    String kcCode = "PY43-KC-001";
    var objective =
        new BossInternalProblem(
            "mainline-objective-1001",
            1,
            1,
            1,
            kcCode,
            "概念知识点的审核判断题",
            List.of(),
            "",
            "",
            1,
            true,
            false,
            List.of(),
            "",
            BossQuestionType.JUDGE,
            List.of(),
            List.of("正确"),
            0);
    when(expeditionRunService.validateProblemRequest(7L, runId, nodeId, 1, false, kcCode))
        .thenReturn(null);
    when(expeditionRunService.findIssuedQuestionFingerprints(7L, runId))
        .thenReturn(Set.of(BossService.questionFingerprint(objective)));
    when(chapterProblemBankRepository.findByKcCodes(List.of(kcCode))).thenReturn(List.of());
    when(mainlinePracticeRepository.findForBossFallback(1, kcCode))
        .thenReturn(
            List.of(
                new MainlinePracticeQuestion(
                    1001L,
                    1,
                    3,
                    110,
                    kcCode,
                    PracticeQuestionType.JUDGE,
                    objective.statement(),
                    List.of(),
                    List.of("正确"),
                    0,
                    "课程依据",
                    "课件页 1")));
    lenient()
        .when(
            pregenService.claimReady(
                eq(7L),
                eq(1),
                eq(1),
                eq(kcCode),
                anyInt(),
                eq("dual_track"),
                org.mockito.ArgumentMatchers.isNull(),
                eq(runId),
                eq(nodeId)))
        .thenReturn(Optional.of(problemWithTests(1).issueAs("ready-code", kcCode)));
    assertThatThrownBy(
            () ->
                service.fetchProblem(
                    1,
                    new BossProblemRequest(
                        1, null, null, kcCode, "dual_track", null, null, runId, nodeId),
                    7L))
        .isInstanceOf(com.pythontopia.common.error.BusinessException.class)
        .extracting(ex -> ((com.pythontopia.common.error.BusinessException) ex).code())
        .isEqualTo("BOSS_OBJECTIVE_BANK_EMPTY");

    verify(pregenService, never())
        .claimReady(
            eq(7L),
            eq(1),
            eq(1),
            eq(kcCode),
            anyInt(),
            eq("dual_track"),
            org.mockito.ArgumentMatchers.isNull(),
            eq(runId),
            eq(nodeId));
    verifyNoInteractions(graphClient);
  }

  @Test
  @DisplayName("普通远征客观题不触发代码题预取")
  void expeditionObjectiveFallbackSkipsCodePrefetch() {
    String runId = UUID.randomUUID().toString();
    String nodeId = "L0-0";
    String kcCode = "PY43-KC-001";
    when(expeditionRunService.validateProblemRequest(7L, runId, nodeId, 1, true, kcCode))
        .thenReturn(null);
    when(expeditionRunService.findIssuedQuestionFingerprints(7L, runId)).thenReturn(Set.of());
    when(chapterProblemBankRepository.findByKcCodes(List.of(kcCode))).thenReturn(List.of());
    when(mainlinePracticeRepository.findForBossFallback(1, kcCode))
        .thenReturn(
            List.of(
                new MainlinePracticeQuestion(
                    1001L,
                    1,
                    3,
                    110,
                    kcCode,
                    PracticeQuestionType.CHOICE,
                    "客观题",
                    List.of("A", "B"),
                    List.of("A"),
                    0,
                    "解释",
                    "课件页 1")));

    var status =
        service.prefetch(
            1,
            new BossProblemRequest(1, null, null, kcCode, "dual_track", null, null, runId, nodeId),
            7L);

    assertThat(status.status()).isEqualTo("IDLE");
    verifyNoInteractions(pregenService);
  }

  @Test
  @DisplayName("明确请求客观练习时复用无编程题 KC 的课程审核题")
  void objectivePracticeRequestUsesCourseQuestionWithoutGraph() {
    String kcCode = "PY43-KC-003";
    var sourceQuestion =
        new MainlinePracticeQuestion(
            31L,
            1,
            1,
            1,
            kcCode,
            PracticeQuestionType.JUDGE,
            "计算思维求解问题的一般流程可概括为：发现问题→抽象建模→算法设计→程序实现→结果评价。",
            List.of(),
            List.of("正确"),
            0,
            "该流程与课程知识点描述一致。",
            "课件页 10724");
    when(expeditionRunService.findIssuedQuestionFingerprints(7L, null)).thenReturn(Set.of());
    when(mainlinePracticeRepository.findForBossFallback(1, kcCode))
        .thenReturn(List.of(sourceQuestion));

    var problem =
        service.fetchProblem(
            1,
            new BossProblemRequest(1, null, null, kcCode, "objective", null, null, null, null),
            7L);

    assertThat(problem.questionType()).isEqualTo(BossQuestionType.JUDGE);
    assertThat(problem.problemId()).startsWith("issue-").doesNotStartWith("issue-boss-phase-");
    assertThat(problem.toString()).doesNotContain("正确");
    verifyNoInteractions(graphClient);
  }

  @Test
  @DisplayName("明确请求客观练习时不预取编程题")
  void objectivePracticeRequestSkipsCodePrefetch() {
    String kcCode = "PY43-KC-003";
    var status =
        service.prefetch(
            1,
            new BossProblemRequest(1, null, null, kcCode, "objective", null, null, null, null),
            7L);

    assertThat(status.status()).isEqualTo("IDLE");
    verifyNoInteractions(pregenService);
  }

  @Test
  @DisplayName("客观题库耗尽时明确失败，不静默请求代码题生成")
  void objectivePracticeRequestFailsClosedWhenBankIsExhausted() {
    String kcCode = "PY43-KC-003";
    when(mainlinePracticeRepository.findForBossFallback(1, kcCode)).thenReturn(List.of());

    assertThatThrownBy(
            () ->
                service.fetchProblem(
                    1,
                    new BossProblemRequest(
                        1, null, null, kcCode, "objective", null, null, null, null),
                    7L))
        .hasMessageContaining("审核客观题");

    verifyNoInteractions(graphClient);
  }

  @Test
  @DisplayName("客观题 Boss 不预取编程题，不额外启动 Graph 出题")
  void objectiveBossDoesNotStartCodeQuestionPrefetch() {
    String runId = UUID.randomUUID().toString();
    String nodeId = "L9-0";
    String kcCode = "PY43-KC-008";
    when(expeditionRunService.validateProblemRequest(7L, runId, nodeId, 2, true, kcCode))
        .thenReturn(1);
    when(chapterProblemBankRepository.findByKcCodes(List.of(kcCode))).thenReturn(List.of());
    when(mainlinePracticeRepository.findForBossFallback(2, kcCode))
        .thenReturn(
            List.of(
                new MainlinePracticeQuestion(
                    15L,
                    2,
                    1,
                    3,
                    kcCode,
                    PracticeQuestionType.JUDGE,
                    "在 Python 中可以使用 print() 函数把内容输出到屏幕。",
                    List.of(),
                    List.of("正确"),
                    0,
                    "print() 是 Python 的标准输出内置函数。",
                    "通识题（非课件原题）")));

    var status =
        service.prefetch(
            2,
            new BossProblemRequest(2, null, null, kcCode, "dual_track", null, null, runId, nodeId),
            7L);

    assertThat(status.status()).isEqualTo("IDLE");
    verifyNoInteractions(pregenService);
  }

  @Test
  @DisplayName("远征 Boss 题目身份区分阶段但同阶段同题仍复用指纹")
  void bossQuestionFingerprintIsScopedToItsServerIssuedPhase() {
    String questionFingerprint = "a".repeat(64);
    String phaseOneId = BossService.issueProblemId(1);
    String phaseTwoId = BossService.issueProblemId(2);

    assertThat(BossService.bossPhaseFromProblemId(phaseOneId)).isEqualTo(1);
    assertThat(BossService.bossPhaseFromProblemId(phaseTwoId)).isEqualTo(2);
    assertThat(BossService.bossPhaseFromProblemId("issue-" + UUID.randomUUID())).isNull();
    assertThat(BossService.expeditionAttemptFingerprint(questionFingerprint, "L9-0", 1))
        .isEqualTo(BossService.expeditionAttemptFingerprint(questionFingerprint, "L9-0", 1));
    assertThat(BossService.expeditionAttemptFingerprint(questionFingerprint, "L9-0", 1))
        .isNotEqualTo(BossService.expeditionAttemptFingerprint(questionFingerprint, "L9-0", 2));
    assertThat(BossService.expeditionAttemptFingerprint(questionFingerprint, "L0-0", null))
        .isNotEqualTo(BossService.expeditionAttemptFingerprint(questionFingerprint, "L0-1", null));
  }

  @Test
  @DisplayName("学生取题命中全局 approved bank 时不调用 Graph，并为该学生保存判题 payload")
  void approvedBankIsPreferredBeforeOnDemandGraphGeneration() {
    when(learnerTwinService.recommendedDifficulty(7L, "PY43-KC-001")).thenReturn(2);
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
            1, new BossProblemRequest(1, null, 2, "PY43-KC-001", "debug", null, null, null), 7L);

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
  @DisplayName("普通练习跳过学生最近已做的 approved 题")
  void regularPracticeSkipsRecentlyIssuedApprovedQuestion() {
    when(learnerTwinService.recommendedDifficulty(7L, "PY43-KC-001")).thenReturn(2);
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
    when(expeditionRunService.findIssuedQuestionFingerprints(7L, null))
        .thenReturn(Set.of(BossService.questionFingerprint(approved)));
    when(reviewRepository.findApproved(1, 1, "PY43-KC-001", "debug", 2))
        .thenReturn(Optional.of(new ApprovedBossProblem(33L, 2, approved)));
    when(graphClient.requestProblem(
            anyLong(), anyInt(), anyInt(), anyString(), anyInt(), any(), any(), any()))
        .thenReturn(problemWithStageAndDifficulty(1, 2));

    var dto =
        service.fetchProblem(
            1, new BossProblemRequest(1, null, 2, "PY43-KC-001", "debug", null, null, null), 7L);

    assertThat(dto.statement()).isEqualTo("statement");
    verify(expeditionRunService).findIssuedQuestionFingerprints(7L, null);
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
    when(pregenService.claimReady(7L, 1, 1, "PY43-KC-001", 2, "debug", null, null))
        .thenReturn(Optional.of(ready));
    when(learnerTwinService.recommendedDifficulty(7L, "PY43-KC-001")).thenReturn(2);

    var dto =
        service.fetchProblem(
            1, new BossProblemRequest(1, null, 2, "PY43-KC-001", "debug", null, null, null), 7L);

    assertThat(dto.problemId()).isEqualTo("ready-for-user-7");
    verify(reviewRepository).isApproved(ready);
    verifyNoInteractions(graphClient);
  }

  @Test
  @DisplayName("复用的预加载 AI 题已撤销审核时改用课程题库题")
  void revokedReadyProblemFallsBackToTrustedCourseQuestion() {
    BossInternalProblem revoked =
        new BossInternalProblem(
            "ready-revoked",
            1,
            1,
            2,
            "PY43-KC-001",
            "AI generated statement",
            List.of(),
            "2 3",
            "5",
            1,
            true,
            true,
            List.of(new BossTestCasePayload("2 3", "5", false)),
            "print(5)");
    when(pregenService.claimReady(7L, 1, 1, "PY43-KC-001", 2, "dual_track", null, null))
        .thenReturn(Optional.of(revoked));
    when(learnerTwinService.recommendedDifficulty(7L, "PY43-KC-001")).thenReturn(2);
    when(reviewRepository.isApproved(revoked)).thenReturn(false);
    when(chapterProblemBankRepository.findByKcCodes(List.of("PY43-KC-001")))
        .thenReturn(
            List.of(
                new PreferredCodingQuestion(
                    120L,
                    "审核题",
                    "课程题库中的已审核题目",
                    List.of(new CodingCase("2 3", "5")),
                    List.of(new CodingCase("2 3", "5")),
                    "a, b = map(int, input().split())\\nprint(a + b)",
                    "PY43-KC-001")));

    var dto =
        service.fetchProblem(
            1,
            new BossProblemRequest(1, null, 2, "PY43-KC-001", "dual_track", null, null, null),
            7L);

    assertThat(dto.statement()).contains("课程题库 · 审核题");
    assertThat(dto.aiGenerated()).isFalse();
    verify(graphClient)
        .requestProblem(anyLong(), anyInt(), anyInt(), anyString(), anyInt(), any(), any(), any());
  }

  @Test
  @DisplayName("远征取题使用服务端地图节点的阶段、难度和题型")
  void expeditionFetchUsesMapOwnedProblemPolicy() {
    String runId = "123e4567-e89b-12d3-a456-426614174000";
    var policy =
        new ExpeditionRunService.ExpeditionProblemPolicy(
            "L3-1", "PY43-KC-001", 4, 4, "closed_loop", null);
    when(expeditionRunService.resolveProblemRequest(
            7L, runId, "L3-1", 1, false, "PY43-KC-001"))
        .thenReturn(policy);
    when(reviewRepository.findApproved(1, 4, "PY43-KC-001", "closed_loop", 4))
        .thenReturn(Optional.of(new ApprovedBossProblem(33L, 1, problemWithStageAndDifficulty(4, 4))));

    var dto =
        service.fetchProblem(
            1,
            new BossProblemRequest(
                1, "PY43-KC-001", 1, "PY43-KC-001", "debug", null, null, runId, "L3-1"),
            7L);

    assertThat(dto.stage()).isEqualTo(4);
    assertThat(dto.difficulty()).isEqualTo(4);
    verify(pregenService)
        .claimReady(
            7L, 1, 4, "PY43-KC-001", 4, "closed_loop", null, runId, "L3-1");
    verify(reviewRepository).findApproved(1, 4, "PY43-KC-001", "closed_loop", 4);
  }

  @Test
  @DisplayName("远征现场出题先预留节点生成额度再请求 Graph")
  void expeditionOnDemandGenerationReservesBudgetBeforeGraphCall() {
    String runId = "123e4567-e89b-12d3-a456-426614174000";
    var policy =
        new ExpeditionRunService.ExpeditionProblemPolicy(
            "L0-0", "PY43-KC-001", 1, 1, "debug", null);
    when(expeditionRunService.resolveProblemRequest(7L, runId, "L0-0", 1, false, "PY43-KC-001"))
        .thenReturn(policy);
    when(expeditionRunService.reserveProblemGeneration(7L, runId, "L0-0", 1, false, "PY43-KC-001"))
        .thenReturn(41L);
    when(pregenService.claimReady(7L, 1, 1, "PY43-KC-001", 1, "debug", null, runId, "L0-0"))
        .thenReturn(Optional.empty());
    when(reviewRepository.findApproved(1, 1, "PY43-KC-001", "debug", 1))
        .thenReturn(Optional.empty());
    when(pregenService.getPrefetchStatus(7L, 1, 1, "PY43-KC-001", 1, "debug", null, runId, "L0-0"))
        .thenReturn(BossPrefetchStatusDto.idle());
    when(graphClient.requestProblem(anyLong(), eq(1), eq(1), eq("PY43-KC-001"), eq(1), any(),
        eq("debug"), any()))
        .thenReturn(problemWithInput("generated"));

    service.fetchProblem(
        1,
        new BossProblemRequest(4, null, 5, "PY43-KC-001", "debug", null, null, runId, "L0-0"),
        7L);

    var order = org.mockito.Mockito.inOrder(expeditionRunService, graphClient);
    order.verify(expeditionRunService)
        .reserveProblemGeneration(7L, runId, "L0-0", 1, false, "PY43-KC-001");
    order.verify(graphClient)
        .requestProblem(anyLong(), eq(1), eq(1), eq("PY43-KC-001"), eq(1), any(), eq("debug"), any());
  }

  @Test
  @DisplayName("远征后台预取也先预留节点生成额度")
  void expeditionPrefetchReservesBudgetBeforeSchedulingGeneration() {
    String runId = "123e4567-e89b-12d3-a456-426614174000";
    var policy =
        new ExpeditionRunService.ExpeditionProblemPolicy(
            "L0-0", "PY43-KC-001", 1, 1, "debug", null);
    var status = new BossPrefetchStatusDto(77L, "PENDING", 1, null, false, null);
    when(expeditionRunService.resolveProblemRequest(7L, runId, "L0-0", 1, true, "PY43-KC-001"))
        .thenReturn(policy);
    when(expeditionRunService.reserveProblemGeneration(7L, runId, "L0-0", 1, true, "PY43-KC-001"))
        .thenReturn(42L);
    when(pregenService.beginPrefetch(
            7L, 1, 1, "PY43-KC-001", 1, "debug", null, runId, "L0-0"))
        .thenReturn(new EndlessPregenService.PrefetchStart(status, true));

    service.prefetch(
        1,
        new BossProblemRequest(4, null, 5, "PY43-KC-001", "debug", null, null, runId, "L0-0"),
        7L);

    var order = org.mockito.Mockito.inOrder(expeditionRunService, pregenService);
    order.verify(expeditionRunService)
        .reserveProblemGeneration(7L, runId, "L0-0", 1, true, "PY43-KC-001");
    order.verify(pregenService)
        .generatePrefetch(
            eq(77L), eq(7L), eq(1), eq(1), eq("PY43-KC-001"), eq(1), eq("PY43-KC-001"),
            eq("debug"), org.mockito.ArgumentMatchers.isNull(), eq(runId), eq("L0-0"), any(), eq(1));
  }

  @Test
  @DisplayName("同维度预加载仍在生成时不启动第二次同步出题")
  void pendingPrefetchPreventsDuplicateOnDemandGeneration() {
    when(pregenService.getPrefetchStatus(7L, 1, 2, "PY43-KC-001", 2, "debug", null, null))
        .thenReturn(new BossPrefetchStatusDto(7L, "PENDING", 1, null, false, null));

    assertThatThrownBy(
            () ->
                service.fetchProblem(
                    1,
                    new BossProblemRequest(2, null, 2, "PY43-KC-001", "debug", null, null, null),
                    7L))
        .isInstanceOf(com.pythontopia.common.error.BusinessException.class)
        .hasMessageContaining("仍在生成");

    verify(graphClient, never())
        .requestProblem(anyLong(), anyInt(), anyInt(), anyString(), anyInt(), any(), any(), any());
  }

  @Test
  @DisplayName("Graph 出题服务不可用时返回服务错误，不发放通用占位题")
  void graphProblemUnavailableDoesNotIssueGenericGoldenProblem() {
    String privateContent = "private-boss-problem-response-sentinel";
    when(graphClient.requestProblem(
            anyLong(), anyInt(), anyInt(), anyString(), anyInt(), any(), any(), any()))
        .thenThrow(new RestClientException(privateContent));

    Logger logger = (Logger) LoggerFactory.getLogger(BossService.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.setLevel(Level.WARN);
    logger.addAppender(appender);
    try {
      assertThatThrownBy(
              () ->
                  service.fetchProblem(
                      1,
                      new BossProblemRequest(2, null, 2, "PY43-KC-001", "debug", null, null, null),
                      7L))
          .isInstanceOfSatisfying(
              com.pythontopia.common.error.BusinessException.class,
              exception -> {
                assertThat(exception.code()).isEqualTo("BOSS_PROBLEM_GENERATION_UNAVAILABLE");
                assertThat(exception.status())
                    .isEqualTo(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
              });
      assertThat(appender.list)
          .extracting(ILoggingEvent::getFormattedMessage)
          .allSatisfy(message -> assertThat(message).doesNotContain(privateContent));
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }

    verify(pregenService, never())
        .persistConsumed(any(), anyInt(), any(), any(), any(), anyInt(), any(), any());
  }

  @Test
  @DisplayName("Graph 不可用时不把普通课程题伪装成改错题")
  void graphProblemUnavailableDoesNotIssueGenericCourseQuestionForDebugKind() {
    when(graphClient.requestProblem(
            anyLong(), anyInt(), anyInt(), anyString(), anyInt(), any(), any(), any()))
        .thenThrow(new RestClientException("graph down"));
    lenient()
        .when(chapterProblemBankRepository.findByKcCodes(List.of("PY43-KC-001")))
        .thenReturn(
            List.of(
                new PreferredCodingQuestion(
                    120L,
                    "打印和",
                    "读入两个整数并输出它们的和。",
                    List.of(new CodingCase("2 3", "5")),
                    List.of(new CodingCase("2 3", "5")),
                    "a, b = map(int, input().split())\\nprint(a + b)",
                    "PY43-KC-001")));

    assertThatThrownBy(
            () ->
                service.fetchProblem(
                    1,
                    new BossProblemRequest(1, null, 2, "PY43-KC-001", "debug", null, null, null),
                    7L))
        .isInstanceOfSatisfying(
            com.pythontopia.common.error.BusinessException.class,
            exception ->
                assertThat(exception.code()).isEqualTo("BOSS_PROBLEM_GENERATION_UNAVAILABLE"));

    verify(pregenService, never())
        .persistConsumed(any(), anyInt(), any(), any(), any(), anyInt(), any(), any());
  }

  @Test
  @DisplayName("同一题请求仍在 Graph 生成时，重试不会再发起一次生成")
  void concurrentRetryForSameProblemDoesNotStartAnotherGraphCall() throws Exception {
    CountDownLatch graphStarted = new CountDownLatch(1);
    CountDownLatch releaseGraph = new CountDownLatch(1);
    when(learnerTwinService.recommendedDifficulty(7L, "PY43-KC-001")).thenReturn(2);
    when(graphClient.requestProblem(
            anyLong(), anyInt(), anyInt(), anyString(), anyInt(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              graphStarted.countDown();
              if (!releaseGraph.await(3, TimeUnit.SECONDS))
                throw new IllegalStateException("test timed out");
              return problemWithStageAndDifficulty(2, 2);
            });
    BossProblemRequest request =
        new BossProblemRequest(2, null, 2, "PY43-KC-001", "debug", null, null, null);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<?> first = executor.submit(() -> service.fetchProblem(1, request, 7L));
      assertThat(graphStarted.await(2, TimeUnit.SECONDS)).isTrue();

      assertThatThrownBy(() -> service.fetchProblem(1, request, 7L))
          .isInstanceOfSatisfying(
              com.pythontopia.common.error.BusinessException.class,
              exception ->
                  assertThat(exception.code()).isEqualTo("BOSS_PROBLEM_GENERATION_PENDING"));

      releaseGraph.countDown();
      assertThat(first.get(3, TimeUnit.SECONDS)).isNotNull();
      verify(graphClient, times(1))
          .requestProblem(
              anyLong(), anyInt(), anyInt(), anyString(), anyInt(), any(), any(), any());
    } finally {
      releaseGraph.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  @DisplayName("完成后再次使用同一请求 ID 会复用原题而不是重新出题")
  void completedRequestIdReturnsTheSameProblem() {
    when(learnerTwinService.recommendedDifficulty(7L, "PY43-KC-001")).thenReturn(2);
    when(graphClient.requestProblem(
            anyLong(), anyInt(), anyInt(), anyString(), anyInt(), any(), any(), any()))
        .thenReturn(problemWithInputAndDifficulty("generated-once", 1, 2));
    String requestId = UUID.randomUUID().toString();
    BossProblemRequest request =
        new BossProblemRequest(
            1, null, 2, "PY43-KC-001", "debug", null, null, null, null, requestId);

    var first = service.fetchProblem(1, request, 7L);
    var retry = service.fetchProblem(1, request, 7L);

    assertThat(retry.problemId()).isEqualTo(first.problemId());
    verify(graphClient, times(1))
        .requestProblem(anyLong(), anyInt(), anyInt(), anyString(), anyInt(), any(), any(), any());
  }

  @Test
  @DisplayName("含隐藏测试的进程缓存严格限制在 256 题")
  void problemCacheRemainsBounded() {
    when(learnerTwinService.recommendedDifficulty(7L, "PY43-KC-001")).thenReturn(2);
    BossInternalProblem approved =
        new BossInternalProblem(
            "approved-33-v2",
            1,
            1,
            2,
            "PY43-KC-001",
            "approved statement",
            List.of(),
            "2 3",
            "5",
            1,
            true,
            true,
            List.of(new BossTestCasePayload("2 3", "5", false)),
            "print(5)");
    when(reviewRepository.findApproved(1, 1, "PY43-KC-001", "debug", 2))
        .thenReturn(Optional.of(new ApprovedBossProblem(33L, 2, approved)));
    BossProblemRequest request =
        new BossProblemRequest(1, null, 2, "PY43-KC-001", "debug", null, null, null);

    for (int i = 0; i < 300; i++) service.fetchProblem(1, request, 7L);

    assertThat((Map<?, ?>) ReflectionTestUtils.getField(service, "cache")).hasSize(256);
  }

  @Test
  @DisplayName("Graph 出题不可用时复用同 KC 的课程题库内容与隐藏用例")
  void graphProblemUnavailableFallsBackToTrustedCourseQuestion() {
    when(graphClient.requestProblem(
            anyLong(), anyInt(), anyInt(), anyString(), anyInt(), any(), any(), any()))
        .thenThrow(new RestClientException("graph down"));
    when(chapterProblemBankRepository.findByKcCodes(List.of("PY43-KC-001")))
        .thenReturn(
            List.of(
                new PreferredCodingQuestion(
                    120L,
                    "打印和",
                    "读入两个整数并输出它们的和。",
                    List.of(new CodingCase("2 3", "5")),
                    List.of(new CodingCase("2 3", "5"), new CodingCase("10 -4", "6")),
                    "a, b = map(int, input().split())\\nprint(a + b)",
                    "PY43-KC-001")));

    var problem =
        service.fetchProblem(
            1, new BossProblemRequest(1, null, 3, "PY43-KC-001", null, null, null, null), 7L);

    assertThat(problem.statement()).contains("课程题库 · 打印和");
    assertThat(problem.kcCode()).isEqualTo("PY43-KC-001");
    assertThat(problem.aiGenerated()).isFalse();
    assertThat(problem.reviewed()).isTrue();
    assertThat(problem.totalTestCases()).isEqualTo(2);
    verifyNoInteractions(judgeCaseEvaluator);
  }

  @Test
  @DisplayName("远征跳过本局已发题目并选择未重复课程题")
  void expeditionSkipsRepeatedGraphQuestionAndUsesUnusedGoldenQuestion() {
    String runId = "123e4567-e89b-12d3-a456-426614174000";
    String nodeId = "L0-0";
    var usedGolden =
        new BossInternalProblem(
            "course-problem-120",
            1,
            1,
            1,
            "PY43-KC-001",
            "课程题库 · 已用\n\nstatement",
            List.of(),
            "2 3",
            "5",
            1,
            true,
            false,
            List.of(new BossTestCasePayload("2 3", "5", false)),
            "ref");
    when(expeditionRunService.findIssuedQuestionFingerprints(7L, runId))
        .thenReturn(
            Set.of(
                BossService.questionFingerprint(problemWithStage(1)),
                BossService.questionFingerprint(usedGolden)));
    when(graphClient.requestProblem(
            anyLong(), anyInt(), anyInt(), anyString(), anyInt(), any(), any(), any()))
        .thenReturn(problemWithStage(1));
    when(chapterProblemBankRepository.findByKcCodes(List.of("PY43-KC-001")))
        .thenReturn(
            List.of(
                new PreferredCodingQuestion(
                    120L,
                    "已用",
                    "statement",
                    List.of(new CodingCase("2 3", "5")),
                    List.of(new CodingCase("2 3", "5")),
                    "ref",
                    "PY43-KC-001"),
                new PreferredCodingQuestion(
                    121L,
                    "新题",
                    "different statement",
                    List.of(new CodingCase("4 5", "9")),
                    List.of(new CodingCase("4 5", "9")),
                    "ref",
                    "PY43-KC-001")));

    var problem =
        service.fetchProblem(
            1,
            new BossProblemRequest(1, "PY43-KC-001", 1, null, null, null, null, runId, nodeId),
            7L);

    assertThat(problem.statement()).contains("课程题库 · 新题");
  }

  @Test
  @DisplayName("Graph 重复且课程题库无新题时返回可重试错误")
  void expeditionDoesNotReissueRepeatedQuestionWhenNoGoldenFallbackExists() {
    String runId = "123e4567-e89b-12d3-a456-426614174000";
    when(expeditionRunService.findIssuedQuestionFingerprints(7L, runId))
        .thenReturn(Set.of(BossService.questionFingerprint(problemWithStage(1))));
    when(graphClient.requestProblem(
            anyLong(), anyInt(), anyInt(), anyString(), anyInt(), any(), any(), any()))
        .thenReturn(problemWithStage(1));
    when(chapterProblemBankRepository.findByKcCodes(List.of("PY43-KC-001"))).thenReturn(List.of());

    assertThatThrownBy(
            () ->
                service.fetchProblem(
                    1,
                    new BossProblemRequest(
                        1, "PY43-KC-001", 1, null, null, null, null, runId, "L0-0"),
                    7L))
        .isInstanceOf(com.pythontopia.common.error.BusinessException.class)
        .hasMessageContaining("没有新的可用题目");
  }

  @Test
  @DisplayName("Graph 返回空题目时也使用同 KC 课程题库兜底")
  void emptyGraphProblemFallsBackToTrustedCourseQuestion() {
    when(graphClient.requestProblem(
            anyLong(), anyInt(), anyInt(), anyString(), anyInt(), any(), any(), any()))
        .thenReturn(null);
    when(chapterProblemBankRepository.findByKcCodes(List.of("PY43-KC-001")))
        .thenReturn(
            List.of(
                new PreferredCodingQuestion(
                    121L,
                    "列表求和",
                    "输出列表元素之和。",
                    List.of(new CodingCase("2 3", "5")),
                    List.of(new CodingCase("2 3", "5")),
                    "a, b = map(int, input().split())\\nprint(a + b)",
                    "PY43-KC-001")));

    var problem =
        service.fetchProblem(
            1, new BossProblemRequest(1, null, 3, "PY43-KC-001", null, null, null, null), 7L);

    assertThat(problem.statement()).contains("课程题库 · 列表求和");
    assertThat(problem.kcCode()).isEqualTo("PY43-KC-001");
    assertThat(problem.aiGenerated()).isFalse();
  }

  @Test
  @DisplayName("同一图生成 ID 不会跨用户或同用户复用隐藏测试")
  void generatedProblemsReceiveUniqueUserScopedIds() {
    when(graphClient.requestProblem(
            anyLong(), anyInt(), anyInt(), anyString(), anyInt(), any(), any(), any()))
        .thenReturn(
            problemWithInputAndKc("private-user-7", "PY43-KC-001"),
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

    service.evaluateAttempt(attemptRequest(user7First.problemId(), 1, 1, "print(1)", 1), 7L);
    service.evaluateAttempt(attemptRequest(user7Second.problemId(), 1, 1, "print(1)", 1), 7L);
    service.evaluateAttempt(attemptRequest(user8.problemId(), 1, 1, "print(1)", 1), 8L);

    ArgumentCaptor<List<BossTestCasePayload>> cases = ArgumentCaptor.forClass(List.class);
    verify(graphClient, org.mockito.Mockito.times(3))
        .evaluateAttempt(anyString(), anyString(), cases.capture(), anyInt());
    assertThat(cases.getAllValues())
        .extracting(testCases -> testCases.get(0).input())
        .containsExactly("private-user-7", "private-user-8", "private-user-8");
  }

  @Test
  @DisplayName("Graph 返回不同知识点的题目时不把它签发为当前 KC")
  void graphProblemWithMismatchedKcIsRejectedBeforePersistence() {
    when(graphClient.requestProblem(
            anyLong(), anyInt(), anyInt(), anyString(), anyInt(), any(), any(), any()))
        .thenReturn(problemWithInputAndKc("private-mismatch", "PY43-KC-002"));

    assertThatThrownBy(
            () ->
                service.fetchProblem(
                    1,
                    new BossProblemRequest(1, null, 1, "PY43-KC-001", "debug", null, null, null),
                    7L))
        .isInstanceOfSatisfying(
            com.pythontopia.common.error.BusinessException.class,
            exception -> {
              assertThat(exception.code()).isEqualTo("BOSS_PROBLEM_CONTEXT_MISMATCH");
              assertThat(exception.status())
                  .isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
            });

    verify(pregenService, never())
        .persistConsumed(any(), anyInt(), any(), any(), any(), anyInt(), any(), any());
  }

  @Test
  @DisplayName("Graph 返回跨 KC 题目时使用同 KC 课程题库兜底")
  void graphProblemWithMismatchedKcFallsBackToGoldenForRequestedKc() {
    when(graphClient.requestProblem(
            anyLong(), anyInt(), anyInt(), anyString(), anyInt(), any(), any(), any()))
        .thenReturn(problemWithInputAndKc("private-mismatch", "PY43-KC-002"));
    when(chapterProblemBankRepository.findByKcCodes(List.of("PY43-KC-001")))
        .thenReturn(
            List.of(
                new PreferredCodingQuestion(
                    121L,
                    "目标 KC 课程题",
                    "目标知识点题目说明。",
                    List.of(new CodingCase("2 3", "5")),
                    List.of(new CodingCase("2 3", "5")),
                    "print(5)",
                    "PY43-KC-001")));

    var dto =
        service.fetchProblem(
            1,
            new BossProblemRequest(1, null, 1, "PY43-KC-001", "dual_track", null, null, null),
            7L);

    assertThat(dto.statement()).contains("目标 KC 课程题");
    assertThat(dto.kcCode()).isEqualTo("PY43-KC-001");
    assertThat(dto.difficulty()).isEqualTo(1);
    assertThat(dto.aiGenerated()).isFalse();
    verify(pregenService)
        .persistConsumed(
            eq(7L),
            eq(1),
            eq("PY43-KC-001"),
            eq("dual_track"),
            any(),
            eq(1),
            org.mockito.ArgumentMatchers.argThat(p -> p.kcCode().equals("PY43-KC-001")),
            org.mockito.ArgumentMatchers.isNull());
    verify(reviewRepository, never())
        .approveAutomaticallyIfNotDuplicate(
            anyLong(), any(), anyInt(), anyInt(), anyString(), anyInt(), any());
  }

  @Test
  @DisplayName("Graph 返回难度不匹配题目时使用目标难度的课程题库兜底")
  void graphProblemWithMismatchedDifficultyFallsBackToGoldenAtTargetDifficulty() {
    when(learnerTwinService.recommendedDifficulty(7L, "PY43-KC-001")).thenReturn(2);
    when(graphClient.requestProblem(
            anyLong(), anyInt(), anyInt(), anyString(), eq(2), any(), any(), any()))
        .thenReturn(problemWithStageAndDifficulty(1, 4));
    when(chapterProblemBankRepository.findByKcCodes(List.of("PY43-KC-001")))
        .thenReturn(
            List.of(
                new PreferredCodingQuestion(
                    121L,
                    "目标难度课程题",
                    "目标知识点题目说明。",
                    List.of(new CodingCase("2 3", "5")),
                    List.of(new CodingCase("2 3", "5")),
                    "print(5)",
                    "PY43-KC-001")));

    var dto =
        service.fetchProblem(
            1,
            new BossProblemRequest(1, null, 5, "PY43-KC-001", "dual_track", null, null, null),
            7L);

    assertThat(dto.statement()).contains("目标难度课程题");
    assertThat(dto.kcCode()).isEqualTo("PY43-KC-001");
    assertThat(dto.difficulty()).isEqualTo(2);
    assertThat(dto.aiGenerated()).isFalse();
    verify(reviewRepository, never())
        .approveAutomaticallyIfNotDuplicate(
            anyLong(), any(), anyInt(), anyInt(), anyString(), anyInt(), any());
  }

  @Test
  @DisplayName("approved bank 只有邻近难度题时不签发该题")
  void approvedBankDoesNotIssueQuestionAtDifferentDifficulty() {
    when(learnerTwinService.recommendedDifficulty(7L, "PY43-KC-001")).thenReturn(2);
    BossInternalProblem approvedAtWrongDifficulty =
        new BossInternalProblem(
            "approved-33-v2",
            1,
            4,
            1,
            "PY43-KC-001",
            "wrong difficulty approved statement",
            List.of("hint"),
            "2 3",
            "5",
            1,
            true,
            true,
            List.of(new BossTestCasePayload("2 3", "5", false)),
            "print(5)");
    when(reviewRepository.findApproved(1, 4, "PY43-KC-001", "debug", 2))
        .thenReturn(Optional.of(new ApprovedBossProblem(33L, 2, approvedAtWrongDifficulty)));
    when(graphClient.requestProblem(
            anyLong(), anyInt(), anyInt(), anyString(), eq(2), any(), any(), any()))
        .thenReturn(problemWithStageAndDifficulty(4, 2));

    var dto =
        service.fetchProblem(
            1, new BossProblemRequest(4, null, 5, "PY43-KC-001", "debug", null, null, null), 7L);

    assertThat(dto.statement()).isEqualTo("statement");
    assertThat(dto.difficulty()).isEqualTo(2);
    assertThat(dto.aiGenerated()).isTrue();
    verify(graphClient)
        .requestProblem(anyLong(), anyInt(), anyInt(), anyString(), eq(2), any(), any(), any());
  }

  @Test
  @DisplayName("approved bank 载荷跨 KC 时跳过并向 Graph 请求目标 KC 题目")
  void approvedBankPayloadWithWrongKcFallsThroughToGraph() {
    BossInternalProblem approvedAtWrongKc =
        new BossInternalProblem(
            "approved-33-v2",
            1,
            1,
            1,
            "PY43-KC-002",
            "wrong KC approved statement",
            List.of("hint"),
            "2 3",
            "5",
            1,
            true,
            true,
            List.of(new BossTestCasePayload("2 3", "5", false)),
            "print(5)");
    when(reviewRepository.findApproved(1, 1, "PY43-KC-001", "debug", 1))
        .thenReturn(Optional.of(new ApprovedBossProblem(33L, 2, approvedAtWrongKc)));
    when(graphClient.requestProblem(
            anyLong(), anyInt(), anyInt(), eq("PY43-KC-001"), eq(1), any(), any(), any()))
        .thenReturn(problemWithStageAndDifficulty(1, 1));

    var dto =
        service.fetchProblem(
            1, new BossProblemRequest(1, null, 4, "PY43-KC-001", "debug", null, null, null), 7L);

    assertThat(dto.problemId()).startsWith("issue-");
    assertThat(dto.kcCode()).isEqualTo("PY43-KC-001");
    assertThat(dto.difficulty()).isEqualTo(1);
    verify(graphClient)
        .requestProblem(anyLong(), anyInt(), anyInt(), eq("PY43-KC-001"), eq(1), any(), any(), any());
  }

  @Test
  @DisplayName("其他用户不能用猜到的题目 ID 读取已发放题目的隐藏测试")
  void generatedProblemCannotBeEvaluatedByAnotherUser() {
    when(graphClient.requestProblem(
            anyLong(), anyInt(), anyInt(), anyString(), anyInt(), any(), any(), any()))
        .thenReturn(problemWithInput("private-user-7"));
    var problem =
        service.fetchProblem(
            1, new BossProblemRequest(1, null, 1, null, null, null, null, null), 7L);

    service.evaluateAttempt(attemptRequest(problem.problemId(), 1, 1, "print(1)", 1), 8L);

    verify(graphClient, never()).evaluateAttempt(anyString(), anyString(), anyList(), anyInt());
    verify(pregenService).findByProblemId(8L, problem.problemId());
    verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("AI 题未获自动审核通过时不签发，且没有课程题库兜底时返回不可用")
  void unapprovedGeneratedProblemIsNotIssued() {
    when(graphClient.requestProblem(
            anyLong(), anyInt(), anyInt(), anyString(), anyInt(), any(), any(), any()))
        .thenReturn(problemWithTests(1));
    when(reviewRepository.approveAutomaticallyIfNotDuplicate(
            anyLong(), any(), anyInt(), anyInt(), anyString(), anyInt(), any()))
        .thenReturn(false);
    when(reviewRepository.isApproved(any())).thenReturn(false);

    assertThatThrownBy(
            () ->
                service.fetchProblem(
                    1, new BossProblemRequest(1, null, 1, null, null, null, null, null), 7L))
        .isInstanceOf(com.pythontopia.common.error.BusinessException.class)
        .hasMessageContaining("尚未通过审核");

    verify(pregenService, never())
        .persistConsumed(any(), anyInt(), any(), any(), any(), anyInt(), any(), any());
  }

  @Test
  @DisplayName("远征题绑定活跃 run，奖励由服务端判题结果记录")
  void verifiedAttemptIsBoundToActiveRun() {
    String runId = "123e4567-e89b-12d3-a456-426614174000";
    when(expeditionRunService.isActiveRun(7L, runId)).thenReturn(true);
    when(graphClient.requestProblem(
            anyLong(), anyInt(), anyInt(), anyString(), anyInt(), any(), any(), any()))
        .thenReturn(problemWithInput("server-owned"));
    when(graphClient.evaluateAttempt(anyString(), anyString(), anyList(), anyInt()))
        .thenReturn(new BossEvaluateResultDto("ignored", true, 1, 1, null, null, List.of()));
    BossInternalProblem issuedProblem =
        problemWithInput("server-owned").issueAs("issue-id", "PY43-KC-001");
    when(pregenService.findExpeditionBinding(eq(7L), anyString()))
        .thenReturn(Optional.of(binding(runId, "L0-0", 1, 1, issuedProblem)));
    when(expeditionScoreService.recordVerifiedAttempt(
            eq(7L),
            eq(runId),
            anyString(),
            anyString(),
            eq(true),
            eq(100),
            any(LearningEvent.class)))
        .thenReturn(new ExpeditionScoreService.VerifiedAttemptResult(true, true));

    BossProblemRequest request =
        new BossProblemRequest(1, null, 1, null, null, null, null, runId, "L0-0");
    var problem = service.fetchProblem(1, request, 7L);
    service.evaluateAttempt(attemptRequest(problem.problemId(), 1, 1, "print(1)", 1), 7L);
    BossInternalProblem internalProblem =
        problemWithInput("server-owned").issueAs(problem.problemId(), "PY43-KC-001");

    verify(pregenService)
        .persistConsumed(
            eq(7L),
            eq(1),
            eq("PY43-KC-001"),
            eq("dual_track"),
            org.mockito.ArgumentMatchers.isNull(),
            eq(1),
            org.mockito.ArgumentMatchers.argThat(p -> p.problemId().equals(problem.problemId())),
            eq(runId),
            eq("L0-0"));
    verify(expeditionScoreService)
        .recordVerifiedAttempt(
            eq(7L),
            eq(runId),
            eq(problem.problemId()),
            eq(BossService.questionFingerprint(internalProblem)),
            eq(true),
            eq(100),
            any(LearningEvent.class));
  }

  @Test
  @DisplayName("已失效的远征 run 不得发放可计分题目")
  void staleExpeditionRunCannotRequestProblem() {
    String staleRunId = "123e4567-e89b-12d3-a456-426614174001";
    org.mockito.Mockito.doThrow(
            new com.pythontopia.common.error.BusinessException(
                org.springframework.http.HttpStatus.CONFLICT,
                "EXPEDITION_RUN_CHANGED",
                "远征存档已变化，请刷新后重试"))
        .when(expeditionRunService)
        .resolveProblemRequest(7L, staleRunId, "L0-0", 1, false, "PY43-KC-001");
    assertThatThrownBy(
            () ->
                service.fetchProblem(
                    1,
                    new BossProblemRequest(1, null, 1, null, null, null, null, staleRunId, "L0-0"),
                    7L))
        .isInstanceOf(com.pythontopia.common.error.BusinessException.class)
        .hasMessageContaining("远征存档");
    verifyNoInteractions(graphClient, pregenService, reviewRepository);
  }

  @Test
  @DisplayName("READY 题的服务端章节与阶段不匹配时不能重新标记后发给远征")
  void readyProblemCannotBeRelabeledToAnotherStage() {
    when(pregenService.claimReady(7L, 1, 2, "PY43-KC-001", 2, "debug", null, null))
        .thenReturn(Optional.of(problemWithInput("ready", 1)));

    assertThatThrownBy(
            () ->
                service.fetchProblem(
                    1,
                    new BossProblemRequest(2, null, 2, "PY43-KC-001", "debug", null, null, null),
                    7L))
        .isInstanceOf(com.pythontopia.common.error.BusinessException.class)
        .hasMessageContaining("阶段");
    verifyNoInteractions(reviewRepository, graphClient);
  }

  @Test
  @DisplayName("已打开的远征题难度不再匹配当前策略时不继续签发")
  void openExpeditionProblemMustMatchCurrentDifficulty() {
    String runId = "123e4567-e89b-12d3-a456-426614174000";
    String nodeId = "L0-0";
    when(learnerTwinService.recommendedDifficulty(7L, "PY43-KC-001")).thenReturn(2);
    when(expeditionRunService.validateProblemRequest(7L, runId, nodeId, 1, false, "PY43-KC-001"))
        .thenReturn(null);
    when(pregenService.findOpenExpeditionProblem(7L, runId, nodeId, null))
        .thenReturn(Optional.of(problemWithStageAndDifficulty(1, 1)));

    assertThatThrownBy(
            () ->
                service.fetchProblem(
                    1,
                    new BossProblemRequest(
                        1, null, 1, "PY43-KC-001", "dual_track", null, null, runId, nodeId),
                    7L))
        .isInstanceOfSatisfying(
            com.pythontopia.common.error.BusinessException.class,
            exception -> assertThat(exception.code()).isEqualTo("BOSS_PROBLEM_DIFFICULTY_MISMATCH"));

    verify(pregenService, never())
        .claimReady(anyLong(), anyInt(), anyInt(), anyString(), anyInt(), any(), any(), anyString(), anyString());
    verifyNoInteractions(graphClient);
  }

  @Test
  @DisplayName("READY 题载荷难度不匹配时跳过并现场生成目标难度题")
  void readyProblemWithWrongPayloadDifficultyFallsThroughToGraph() {
    when(learnerTwinService.recommendedDifficulty(7L, "PY43-KC-001")).thenReturn(2);
    when(pregenService.claimReady(7L, 1, 1, "PY43-KC-001", 2, "debug", null, null))
        .thenReturn(Optional.of(problemWithStageAndDifficulty(1, 1)));
    when(graphClient.requestProblem(
            anyLong(), anyInt(), anyInt(), anyString(), eq(2), any(), any(), any()))
        .thenReturn(problemWithStageAndDifficulty(1, 2));

    var dto =
        service.fetchProblem(
            1, new BossProblemRequest(1, null, 5, "PY43-KC-001", "debug", null, null, null), 7L);

    assertThat(dto.problemId()).startsWith("issue-");
    assertThat(dto.difficulty()).isEqualTo(2);
    verify(graphClient)
        .requestProblem(anyLong(), anyInt(), anyInt(), anyString(), eq(2), any(), any(), any());
  }

  @Test
  @DisplayName("READY 题与本局已发题目重复时跳过并生成未重复题")
  void repeatedReadyProblemFallsThroughToUnusedGraphQuestion() {
    when(learnerTwinService.recommendedDifficulty(7L, "PY43-KC-001")).thenReturn(2);
    BossInternalProblem repeated =
        new BossInternalProblem(
            "ready-duplicate", 1, 1, 2, "PY43-KC-001", "ready statement", List.of(), "", "", 1,
            true, true, List.of(new BossTestCasePayload("ready", "expected", false)), "ref");
    when(expeditionRunService.findIssuedQuestionFingerprints(7L, null))
        .thenReturn(Set.of(BossService.questionFingerprint(repeated)));
    when(pregenService.claimReady(7L, 1, 1, "PY43-KC-001", 2, "debug", null, null))
        .thenReturn(Optional.of(repeated));
    when(graphClient.requestProblem(
            anyLong(), anyInt(), anyInt(), anyString(), eq(2), any(), any(), any()))
        .thenReturn(problemWithInputAndDifficulty("graph-new", 1, 2));

    var dto =
        service.fetchProblem(
            1, new BossProblemRequest(1, null, 5, "PY43-KC-001", "debug", null, null, null), 7L);

    assertThat(dto.problemId()).startsWith("issue-");
    assertThat(dto.difficulty()).isEqualTo(2);
    verify(graphClient)
        .requestProblem(anyLong(), anyInt(), anyInt(), anyString(), eq(2), any(), any(), any());
  }

  @Test
  @DisplayName("提交题目时章节或阶段必须与服务端签发内容一致")
  void attemptContextMustMatchTheIssuedProblem() {
    when(pregenService.findByProblemId(7L, "p1")).thenReturn(Optional.of(problemWithTests(2)));

    assertThatThrownBy(
            () -> service.evaluateAttempt(attemptRequest("p1", 1, 2, "print(5)", 10), 7L))
        .isInstanceOf(com.pythontopia.common.error.BusinessException.class)
        .hasMessageContaining("签发");
    verify(graphClient, never()).evaluateAttempt(anyString(), anyString(), anyList(), anyInt());
  }

  @Test
  @DisplayName("结算后的远征题不得继续调用判题服务")
  void settledExpeditionProblemCannotBeSubmitted() {
    String runId = "123e4567-e89b-12d3-a456-426614174000";
    when(pregenService.findExpeditionBinding(7L, "p1"))
        .thenReturn(
            Optional.of(
                new EndlessPregenService.ExpeditionProblemBinding(
                    runId, "L0-0", 1, 1, "PY43-KC-001", "a".repeat(64), null)));
    when(expeditionRunService.isActiveRun(7L, runId)).thenReturn(false);

    assertThatThrownBy(
            () -> service.evaluateAttempt(attemptRequest("p1", 1, 1, "print(5)", 10), 7L))
        .isInstanceOf(com.pythontopia.common.error.BusinessException.class)
        .hasMessageContaining("远征存档");
    verify(graphClient, never()).evaluateAttempt(anyString(), anyString(), anyList(), anyInt());
  }

  @Test
  @DisplayName("Boss 题签发后阶段已推进时不能再次判题")
  void bossProblemFromPreviousPhaseCannotBeEvaluatedAgain() {
    String runId = "123e4567-e89b-12d3-a456-426614174000";
    String problemId = BossService.issueProblemId(1);
    BossInternalProblem problem = problemWithTests(1).issueAs(problemId, "PY43-KC-001");
    when(pregenService.findExpeditionBinding(7L, problemId))
        .thenReturn(Optional.of(binding(runId, "L9-0", 1, 1, problem)));
    when(expeditionRunService.isActiveRun(7L, runId)).thenReturn(true);
    when(expeditionRunService.validateProblemRequest(7L, runId, "L9-0", 1, false, "PY43-KC-001"))
        .thenReturn(2);

    assertThatThrownBy(
            () -> service.evaluateAttempt(attemptRequest(problemId, 1, 1, "print(5)", 1), 7L))
        .isInstanceOf(com.pythontopia.common.error.BusinessException.class)
        .hasMessageContaining("绑定");
    verify(graphClient, never()).evaluateAttempt(anyString(), anyString(), anyList(), anyInt());
  }

  @Test
  @DisplayName("判题账本拒绝活动 run 的超额题目时，不发学习事件，也不误报存档过期")
  void verifiedAttemptLimitDoesNotPublishLearningEventOrClaimRunExpired() {
    String runId = "123e4567-e89b-12d3-a456-426614174000";
    when(pregenService.findExpeditionBinding(7L, "p1"))
        .thenReturn(
            Optional.of(
                new EndlessPregenService.ExpeditionProblemBinding(
                    runId,
                    "L0-0",
                    1,
                    1,
                    "PY43-KC-001",
                    BossService.questionFingerprint(problemWithTests(1)),
                    null)));
    when(expeditionRunService.isActiveRun(7L, runId)).thenReturn(true);
    when(pregenService.findByProblemId(7L, "p1")).thenReturn(Optional.of(problemWithTests(1)));
    when(graphClient.evaluateAttempt(eq("p1"), anyString(), anyList(), anyInt()))
        .thenReturn(new BossEvaluateResultDto("p1", true, 1, 1, null, null, List.of()));
    when(expeditionScoreService.recordVerifiedAttempt(
            eq(7L), eq(runId), eq("p1"), anyString(), eq(true), eq(100), any(LearningEvent.class)))
        .thenReturn(new ExpeditionScoreService.VerifiedAttemptResult(false, false));

    assertThatThrownBy(
            () -> service.evaluateAttempt(attemptRequest("p1", 1, 1, "print(5)", 10), 7L))
        .isInstanceOf(com.pythontopia.common.error.BusinessException.class)
        .hasMessageContaining("判题次数");

    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  @DisplayName("远征判题把结果交给事务记账服务，幂等响应不重复发布事件")
  void expeditionProblemPublishesOnlyFirstFailureAndFirstPass() {
    String runId = "123e4567-e89b-12d3-a456-426614174000";
    when(pregenService.findExpeditionBinding(7L, "p1"))
        .thenReturn(
            Optional.of(
                new EndlessPregenService.ExpeditionProblemBinding(
                    runId,
                    "L0-0",
                    1,
                    1,
                    "PY43-KC-001",
                    BossService.questionFingerprint(problemWithTests(3)),
                    null)));
    when(expeditionRunService.isActiveRun(7L, runId)).thenReturn(true);
    when(pregenService.findByProblemId(7L, "p1")).thenReturn(Optional.of(problemWithTests(3)));
    when(graphClient.evaluateAttempt(eq("p1"), anyString(), anyList(), anyInt()))
        .thenReturn(
            new BossEvaluateResultDto("p1", false, 0, 1, "hint", null, List.of(0)),
            new BossEvaluateResultDto("p1", true, 1, 1, null, null, List.of()),
            new BossEvaluateResultDto("p1", true, 1, 1, null, null, List.of()));
    when(expeditionScoreService.recordVerifiedAttempt(
            eq(7L), eq(runId), eq("p1"), anyString(), eq(false), eq(0), any(LearningEvent.class)))
        .thenReturn(new ExpeditionScoreService.VerifiedAttemptResult(true, true));
    when(expeditionScoreService.recordVerifiedAttempt(
            eq(7L), eq(runId), eq("p1"), anyString(), eq(true), eq(100), any(LearningEvent.class)))
        .thenReturn(
            new ExpeditionScoreService.VerifiedAttemptResult(true, true),
            new ExpeditionScoreService.VerifiedAttemptResult(true, false));

    UUID firstId = UUID.fromString("123e4567-e89b-12d3-a456-426614174001");
    UUID secondId = UUID.fromString("123e4567-e89b-12d3-a456-426614174002");
    UUID thirdId = UUID.fromString("123e4567-e89b-12d3-a456-426614174003");
    BossAttemptRequest first = new BossAttemptRequest("p1", 1, 1, "print(0)", 1, firstId);
    BossAttemptRequest second = new BossAttemptRequest("p1", 1, 1, "print(5)", 1, secondId);
    BossAttemptRequest third = new BossAttemptRequest("p1", 1, 1, "print(5)", 1, thirdId);
    var firstClaim =
        new BossAttemptIdempotencyService.Claim(UUID.randomUUID(), null, "a".repeat(64));
    var secondClaim =
        new BossAttemptIdempotencyService.Claim(UUID.randomUUID(), null, "b".repeat(64));
    var thirdClaim =
        new BossAttemptIdempotencyService.Claim(UUID.randomUUID(), null, "c".repeat(64));
    when(attemptIdempotencyService.claim(7L, firstId, first)).thenReturn(firstClaim);
    when(attemptIdempotencyService.claim(7L, secondId, second)).thenReturn(secondClaim);
    when(attemptIdempotencyService.claim(7L, thirdId, third)).thenReturn(thirdClaim);
    when(attemptIdempotencyService.complete(eq(7L), eq(firstId), eq(firstClaim), any(), any()))
        .thenAnswer(call -> call.getArgument(3));
    when(attemptIdempotencyService.complete(
            eq(7L),
            eq(secondId),
            eq(secondClaim),
            any(),
            org.mockito.ArgumentMatchers.nullable(LearningEvent.class)))
        .thenAnswer(call -> call.getArgument(3));
    when(attemptIdempotencyService.complete(
            eq(7L),
            eq(thirdId),
            eq(thirdClaim),
            any(),
            org.mockito.ArgumentMatchers.nullable(LearningEvent.class)))
        .thenAnswer(call -> call.getArgument(3));

    var firstResult = service.evaluateAttempt(first, 7L);
    var secondResult = service.evaluateAttempt(second, 7L);
    var thirdResult = service.evaluateAttempt(third, 7L);

    assertThat(firstResult.passed()).isFalse();
    assertThat(secondResult.passed()).isTrue();
    assertThat(thirdResult.passed()).isTrue();
    assertThat(thirdResult.score()).isEqualTo(100);
    ArgumentCaptor<LearningEvent> scoreEvents = ArgumentCaptor.forClass(LearningEvent.class);
    verify(expeditionScoreService, times(3))
        .recordVerifiedAttempt(
            eq(7L),
            eq(runId),
            eq("p1"),
            anyString(),
            anyBoolean(),
            anyInt(),
            scoreEvents.capture());
    assertThat(scoreEvents.getAllValues().get(0)).isInstanceOf(LearningEvent.PracticeFailed.class);
    assertThat(scoreEvents.getAllValues().get(1))
        .isInstanceOf(LearningEvent.PracticeSucceeded.class);
    assertThat(scoreEvents.getAllValues().get(2))
        .isInstanceOf(LearningEvent.PracticeSucceeded.class);
    verify(attemptIdempotencyService)
        .complete(
            eq(7L), eq(firstId), eq(firstClaim), any(), org.mockito.ArgumentMatchers.isNull());
    verify(attemptIdempotencyService)
        .complete(
            eq(7L), eq(secondId), eq(secondClaim), any(), org.mockito.ArgumentMatchers.isNull());
    verify(attemptIdempotencyService)
        .complete(
            eq(7L), eq(thirdId), eq(thirdClaim), any(), org.mockito.ArgumentMatchers.isNull());
    verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("远征题目指纹忽略签发 ID 和难度标签，但包含实际题面与判题用例")
  void expeditionQuestionFingerprintTracksJudgedContent() {
    BossInternalProblem issuedOne = problemWithTests(2);
    BossInternalProblem issuedAgain =
        new BossInternalProblem(
            "issue-another-id",
            1,
            4,
            5,
            "PY43-KC-001",
            "statement",
            List.of("different hint"),
            "2 3",
            "5",
            1,
            true,
            true,
            List.of(new BossTestCasePayload("2 3", "5", false)),
            "different reference solution");
    BossInternalProblem differentQuestion =
        new BossInternalProblem(
            "issue-third-id",
            1,
            1,
            2,
            "PY43-KC-001",
            "statement",
            List.of(),
            "2 3",
            "5",
            1,
            true,
            true,
            List.of(new BossTestCasePayload("2 3", "6", false)),
            "ref");

    assertThat(BossService.questionFingerprint(issuedAgain))
        .isEqualTo(BossService.questionFingerprint(issuedOne));
    assertThat(BossService.questionFingerprint(differentQuestion))
        .isNotEqualTo(BossService.questionFingerprint(issuedOne));
  }

  @Test
  @DisplayName("无课程编程题的合格 KC 仍可开局并由 AI 按同一 KC 出题")
  void selectFrontierAllowsKcWithoutCourseProblemForAiGeneration() {
    var pick = new FrontierPickDto("PY43-KC-001", 1, 1, "尚未评估，先从基础题建立证据");
    when(learnerTwinService.frontier(7L, 1, List.of(), 40))
        .thenReturn(new FrontierSelectResultDto(List.of(pick)));

    var result =
        service.selectFrontier(7L, new FrontierSelectRequest(null, List.of(), 1, List.of(), 1));

    assertThat(result.picks()).containsExactly(pick);
    verifyNoInteractions(chapterProblemBankRepository, graphClient);
  }

  @Test
  @DisplayName("通用远征前沿保留数字孪生排序，由 AI 为缺题 KC 出题")
  void selectFrontierPreservesTwinPriorityForAiGeneratedQuestions() {
    var frontier =
        new FrontierSelectResultDto(
            List.of(
                new FrontierPickDto("PY43-KC-001", 1, 1, "尚未评估"),
                new FrontierPickDto("PY43-KC-016", 2, 1, "尚未评估"),
                new FrontierPickDto("PY43-KC-034", 4, 2, "近期练习薄弱")));
    lenient().when(learnerTwinService.frontier(7L, null, List.of(), 40)).thenReturn(frontier);

    var result =
        service.selectFrontier(7L, new FrontierSelectRequest(null, List.of(), null, List.of(), 1));

    assertThat(result.picks()).containsExactly(frontier.picks().getFirst());
    verify(learnerTwinService).frontier(7L, null, List.of(), 40);
    verifyNoInteractions(chapterProblemBankRepository, graphClient);
  }

  @Test
  @DisplayName("前沿没有满足先修条件的知识点时返回明确说明")
  void selectFrontierExplainsWhenNoKnowledgePointMeetsPrerequisites() {
    when(learnerTwinService.frontier(7L, 5, List.of(), 40))
        .thenReturn(new FrontierSelectResultDto(List.of()));

    assertThatThrownBy(
            () ->
                service.selectFrontier(
                    7L, new FrontierSelectRequest(null, List.of(), 5, List.of(), 4)))
        .isInstanceOfSatisfying(
            com.pythontopia.common.error.BusinessException.class,
            exception -> assertThat(exception.getMessage()).contains("主线地图"));
    verifyNoInteractions(chapterProblemBankRepository, graphClient);
  }

  @Test
  @DisplayName("远征开局允许有同 KC 客观题的第一章概念 KC")
  void expeditionFrontierKeepsPreferredConceptKcWithObjectiveQuestion() {
    var candidates =
        new FrontierSelectResultDto(
            List.of(
                new FrontierPickDto("PY43-KC-001", 1, 1, "尚未评估"),
                new FrontierPickDto("PY43-KC-016", 2, 1, "尚未评估")));
    when(learnerTwinService.frontier(7L, null, List.of(), 40, "PY43-KC-001"))
        .thenReturn(candidates);
    when(mainlinePracticeRepository.findForBossFallback(1, "PY43-KC-001"))
        .thenReturn(
            List.of(
                new MainlinePracticeQuestion(
                    1001L,
                    1,
                    3,
                    110,
                    "PY43-KC-001",
                    PracticeQuestionType.CHOICE,
                    "选择题",
                    List.of("A", "B"),
                    List.of("A"),
                    0,
                    "解释",
                    "课件页 1")));

    var result = service.selectFrontier(7L, null, "PY43-KC-001");

    assertThat(result.picks())
        .extracting(FrontierPickDto::kcCode)
        .containsExactly("PY43-KC-001", "PY43-KC-016");
    verifyNoInteractions(chapterProblemBankRepository, graphClient);
  }

  @Test
  @DisplayName("指定 KC 因缺少客观题不可用时不替换为其他知识点")
  void expeditionFrontierDoesNotReplaceUnavailablePreferredKc() {
    when(learnerTwinService.frontier(7L, null, List.of(), 40, "PY43-KC-001"))
        .thenReturn(
            new FrontierSelectResultDto(
                List.of(
                    new FrontierPickDto("PY43-KC-001", 1, 1, "复习到期"),
                    new FrontierPickDto("PY43-KC-016", 2, 1, "近期练习薄弱"))));
    when(mainlinePracticeRepository.findForBossFallback(1, "PY43-KC-001"))
        .thenReturn(List.of());

    assertThatThrownBy(() -> service.selectFrontier(7L, null, "PY43-KC-001"))
        .isInstanceOfSatisfying(
            com.pythontopia.common.error.BusinessException.class,
            exception -> {
              assertThat(exception.code()).isEqualTo("EXPEDITION_NO_PRACTICE_PROBLEMS");
              assertThat(exception.status())
                  .isEqualTo(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY);
            });
  }

  @Test
  @DisplayName("远征不放行没有同 KC 客观题的第一章概念 KC")
  void expeditionExplainsWhenOnlyConceptRootsAreEligible() {
    when(learnerTwinService.frontier(7L, null, List.of(), 40, null))
        .thenReturn(
            new FrontierSelectResultDto(
                List.of(
                    new FrontierPickDto("PY43-KC-001", 1, 1, "尚未评估"),
                    new FrontierPickDto("PY43-KC-002", 1, 1, "尚未评估"),
                    new FrontierPickDto("PY43-KC-004", 1, 1, "尚未评估"),
                    new FrontierPickDto("PY43-KC-006", 1, 1, "尚未评估"))));

    assertThatThrownBy(() -> service.selectFrontier(7L, null, null))
        .isInstanceOfSatisfying(
            com.pythontopia.common.error.BusinessException.class,
            exception -> {
              assertThat(exception.code()).isEqualTo("EXPEDITION_NO_PRACTICE_PROBLEMS");
              assertThat(exception.getMessage()).contains("可用练习题");
            });
  }

  @Test
  @DisplayName("聚焦章节没有满足先修条件的 KC 时以明确业务错误拒绝开局")
  void expeditionFrontierRejectsChapterWithoutEligibleKnowledgePoints() {
    when(learnerTwinService.frontier(7L, 1, List.of(), 40, null))
        .thenReturn(new FrontierSelectResultDto(List.of()));

    assertThatThrownBy(() -> service.selectFrontier(7L, 1, null))
        .isInstanceOfSatisfying(
            com.pythontopia.common.error.BusinessException.class,
            exception -> {
              assertThat(exception.code()).isEqualTo("EXPEDITION_NO_PRACTICE_PROBLEMS");
              assertThat(exception.status())
                  .isEqualTo(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(exception.getMessage()).contains("主线地图");
            });
    verifyNoInteractions(chapterProblemBankRepository, graphClient);
  }

  @Test
  @DisplayName("Boss 取题使用数字孪生针对当前 KC 的规则难度")
  void fetchProblemUsesSharedLearnerDifficultyInsteadOfClientDifficulty() {
    when(learnerTwinService.recommendedDifficulty(7L, "PY43-KC-001")).thenReturn(2);
    when(graphClient.requestProblem(
            anyLong(), anyInt(), anyInt(), anyString(), eq(2), any(), any(), any()))
        .thenReturn(problemWithStageAndDifficulty(4, 2));

    service.fetchProblem(
        1, new BossProblemRequest(4, null, 5, "PY43-KC-001", null, null, null, null), 7L);

    verify(learnerTwinService).recommendedDifficulty(7L, "PY43-KC-001");
    verify(graphClient)
        .requestProblem(anyLong(), anyInt(), anyInt(), anyString(), eq(2), any(), any(), any());
  }

  @Test
  @DisplayName("通过 AI 审查的现场题进入共享题库")
  void generatedProblemIsAddedToSharedBank() {
    when(graphClient.requestProblem(
            anyLong(), anyInt(), anyInt(), anyString(), anyInt(), any(), any(), any()))
        .thenReturn(problemWithStage(1));

    service.fetchProblem(
        1, new BossProblemRequest(1, null, 1, "PY43-KC-001", "debug", null, null, null), 7L);

    verify(reviewRepository)
        .approveAutomaticallyIfNotDuplicate(
            eq(7L),
            eq("debug"),
            eq(1),
            eq(1),
            eq("PY43-KC-001"),
            eq(1),
            org.mockito.ArgumentMatchers.argThat(
                (BossInternalProblem problem) -> problem.aiGenerated() && problem.reviewed()));
  }

  @Test
  @DisplayName("Twin 无可用学情时按 stage 使用有界难度兜底")
  void missingTwinAssessmentFallsBackToStageDifficulty() {
    assertThat(
            java.util.stream.IntStream.rangeClosed(1, 5)
                .map(stage -> service.computeTargetDifficulty(7L, "PY43-KC-001", stage))
                .boxed()
                .toList())
        .containsExactly(1, 2, 3, 4, 5);
  }

  @Test
  void targetDifficultyFailureDoesNotLogLearnerIdOrExceptionMessage() {
    long userId = 87654321L;
    String privateDetails = "private-twin-difficulty-error-sentinel";
    when(learnerTwinService.recommendedDifficulty(userId, "PY43-KC-001"))
        .thenThrow(new IllegalStateException(privateDetails));
    Logger logger = (Logger) LoggerFactory.getLogger(BossService.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.setLevel(Level.DEBUG);
    logger.addAppender(appender);
    try {
      assertThat(service.computeTargetDifficulty(userId, "PY43-KC-001", 3)).isEqualTo(3);
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }

    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anySatisfy(message -> assertThat(message).contains("errorType=IllegalStateException"))
        .noneMatch(
            message -> message.contains(Long.toString(userId)) || message.contains(privateDetails));
  }

  @Test
  @DisplayName("预加载轮询与取题使用相同 Twin 难度")
  void prefetchStatusUsesSharedLearnerDifficulty() {
    when(learnerTwinService.recommendedDifficulty(7L, "PY43-KC-001")).thenReturn(2);
    when(pregenService.getPrefetchStatus(7L, 1, 4, "PY43-KC-001", 2, "debug", "1-5", null))
        .thenReturn(BossPrefetchStatusDto.idle());

    service.prefetchStatus(1, 4, "PY43-KC-001", 5, "debug", "1-5", null, 7L);

    verify(pregenService).getPrefetchStatus(7L, 1, 4, "PY43-KC-001", 2, "debug", "1-5", null);
  }

  @Test
  @DisplayName("Boss 个性化信号由 dimensions metadata 覆盖客户端画像字段")
  void problemRequestUsesUnifiedDimensionSignalsAndPreservesRequestQuestKind() {
    when(passiveProfileService.getPersonalizationSignals(7L))
        .thenReturn(
            new LearnerProfileSignals(
                List.of("PY43-KC-009"),
                2,
                0.6,
                List.of("video", "diagram"),
                0.4,
                2.5,
                "NON_CS_HUMANITIES",
                "JOB",
                List.of("PY43-KC-001"),
                0.7,
                -0.5,
                30));
    when(graphClient.requestProblem(
            anyLong(), anyInt(), anyInt(), anyString(), anyInt(), any(), any(), any()))
        .thenReturn(problemWithStage(1));
    ProfileSignalsPayload clientSignals =
        new ProfileSignalsPayload(
            List.of("CLIENT-KC"),
            5,
            1.0,
            List.of("client"),
            1.0,
            "debug",
            "CS",
            "EXAM",
            List.of("PY43-KC-065"),
            0.1,
            0.5,
            60);

    service.fetchProblem(
        1, new BossProblemRequest(1, null, null, null, "review", clientSignals, null, null), 7L);

    ArgumentCaptor<ProfileSignalsPayload> captured =
        ArgumentCaptor.forClass(ProfileSignalsPayload.class);
    verify(graphClient)
        .requestProblem(
            eq(7L),
            eq(1),
            eq(1),
            eq("PY43-KC-001"),
            anyInt(),
            org.mockito.ArgumentMatchers.isNull(),
            eq("review"),
            captured.capture());
    assertThat(captured.getValue())
        .isEqualTo(
            new ProfileSignalsPayload(
                List.of("PY43-KC-009"),
                2,
                0.6,
                List.of("video", "diagram"),
                0.4,
                "debug",
                "NON_CS_HUMANITIES",
                "JOB",
                List.of("PY43-KC-001"),
                0.7,
                -0.5,
                30));
  }

  @Test
  @DisplayName("attempt 通过 → 发布 PracticeSucceeded（闭环回流 KC 掌握度 / 画像）")
  void passingAttemptPublishesPracticeSucceeded() {
    when(pregenService.findByProblemId(7L, "p1")).thenReturn(Optional.of(problemWithTests(4)));
    when(graphClient.evaluateAttempt(eq("p1"), anyString(), anyList(), anyInt()))
        .thenReturn(new BossEvaluateResultDto("p1", true, 1, 1, null, null, List.of()));
    UUID attemptId = UUID.randomUUID();

    service.evaluateAttempt(new BossAttemptRequest("p1", 1, 1, "print(5)", 10, attemptId), 7L);

    ArgumentCaptor<LearningEvent.PracticeSucceeded> captor =
        ArgumentCaptor.forClass(LearningEvent.PracticeSucceeded.class);
    verify(eventPublisher).publishEvent(captor.capture());
    LearningEvent.PracticeSucceeded event = captor.getValue();
    assertThat(event.userId()).isEqualTo(7L);
    assertThat(event.kcCode()).isEqualTo("PY43-KC-001");
    assertThat(event.problemId()).isNull(); // boss 题无 course_problem 数字主键
    assertThat(event.difficulty()).isEqualTo("hard"); // difficulty 4 → hard
    assertThat(event.idempotencyKey())
        .isEqualTo("boss-pass:7:" + BossService.questionFingerprint(problemWithTests(4)));
  }

  @Test
  @DisplayName("同一普通 Boss 题换 attemptId 再次通过仍复用掌握度幂等键")
  void repeatedPassForSameRegularBossProblemSharesMasteryIdempotencyKey() {
    BossInternalProblem problem = problemWithTests(4);
    when(pregenService.findByProblemId(7L, "p1")).thenReturn(Optional.of(problem));
    when(graphClient.evaluateAttempt(eq("p1"), anyString(), anyList(), anyInt()))
        .thenReturn(new BossEvaluateResultDto("p1", true, 1, 1, null, null, List.of()));

    service.evaluateAttempt(
        new BossAttemptRequest("p1", 1, 1, "print(5)", 10, UUID.randomUUID()), 7L);
    service.evaluateAttempt(
        new BossAttemptRequest("p1", 1, 1, "print(5)", 10, UUID.randomUUID()), 7L);

    ArgumentCaptor<LearningEvent.PracticeSucceeded> events =
        ArgumentCaptor.forClass(LearningEvent.PracticeSucceeded.class);
    verify(eventPublisher, times(2)).publishEvent(events.capture());
    assertThat(events.getAllValues())
        .extracting(LearningEvent.PracticeSucceeded::idempotencyKey)
        .containsExactly(
            "boss-pass:7:" + BossService.questionFingerprint(problem),
            "boss-pass:7:" + BossService.questionFingerprint(problem));
  }

  @Test
  @DisplayName("不同用户复用同一 attemptId 时画像事件幂等键仍各自独立")
  void sameAttemptIdAcrossUsersProducesDistinctProfileIdempotencyKeys() {
    when(pregenService.findByProblemId(7L, "p1")).thenReturn(Optional.of(problemWithTests(4)));
    when(pregenService.findByProblemId(8L, "p1")).thenReturn(Optional.of(problemWithTests(4)));
    when(graphClient.evaluateAttempt(eq("p1"), anyString(), anyList(), anyInt()))
        .thenReturn(new BossEvaluateResultDto("p1", true, 1, 1, null, null, List.of()));
    UUID sharedAttemptId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    BossAttemptRequest request =
        new BossAttemptRequest("p1", 1, 1, "print(5)", 10, sharedAttemptId);

    service.evaluateAttempt(request, 7L);
    service.evaluateAttempt(request, 8L);

    ArgumentCaptor<LearningEvent> events = ArgumentCaptor.forClass(LearningEvent.class);
    verify(eventPublisher, times(2)).publishEvent(events.capture());
    assertThat(events.getAllValues())
        .extracting(event -> ((LearningEvent.PracticeSucceeded) event).idempotencyKey())
        .containsExactly(
            "boss-pass:7:" + BossService.questionFingerprint(problemWithTests(4)),
            "boss-pass:8:" + BossService.questionFingerprint(problemWithTests(4)));
  }

  @Test
  @DisplayName("同一 attemptId 重放只判题并发布一次学习事件")
  void replayedAttemptIdDoesNotPublishPracticeOutcomeTwice() {
    when(pregenService.findByProblemId(7L, "p1")).thenReturn(Optional.of(problemWithTests(4)));
    when(graphClient.evaluateAttempt(eq("p1"), anyString(), anyList(), anyInt()))
        .thenReturn(new BossEvaluateResultDto("p1", true, 1, 1, null, null, List.of()));
    UUID attemptId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    BossAttemptRequest request = new BossAttemptRequest("p1", 1, 1, "print(5)", 10, attemptId);
    var claim = new BossAttemptIdempotencyService.Claim(UUID.randomUUID(), null, "a".repeat(64));
    var replay =
        new BossAttemptIdempotencyService.Claim(
            null,
            new com.pythontopia.world.boss.dto.BossAttemptResultDto(
                "p1", true, 1, 1, null, 100, "通过 ✅ 服务端沙箱已确认。"),
            "a".repeat(64));
    when(attemptIdempotencyService.claim(7L, attemptId, request)).thenReturn(claim, replay);
    when(attemptIdempotencyService.complete(eq(7L), eq(attemptId), eq(claim), any(), any()))
        .thenReturn(replay.replay());

    var first = service.evaluateAttempt(request, 7L);
    var second = service.evaluateAttempt(request, 7L);

    verify(graphClient, times(1)).evaluateAttempt(eq("p1"), anyString(), anyList(), anyInt());
    verify(attemptIdempotencyService, times(1))
        .complete(eq(7L), eq(attemptId), eq(claim), any(), any());
    assertThat(second).isEqualTo(first);
  }

  @Test
  @DisplayName("认证用户的判题必须携带 attemptId")
  void authenticatedAttemptRequiresIdempotencyKey() {
    BossAttemptRequest request = new BossAttemptRequest("p1", 1, 1, "print(5)", 10, null);

    assertThatThrownBy(() -> service.evaluateAttempt(request, 7L))
        .isInstanceOfSatisfying(
            com.pythontopia.common.error.BusinessException.class,
            exception -> assertThat(exception.code()).isEqualTo("BOSS_ATTEMPT_ID_REQUIRED"));
    verifyNoInteractions(pregenService, attemptIdempotencyService, graphClient, eventPublisher);
  }

  @Test
  @DisplayName("不能用可猜测的全局审核题 ID 绕过用户题目签发")
  void approvedAttemptRejectsGlobalReviewBankIdNotIssuedToUser() {
    when(pregenService.findByProblemId(7L, "approved-33-v2")).thenReturn(Optional.empty());
    var result =
        service.evaluateAttempt(attemptRequest("approved-33-v2", 1, 1, "print(5)", 10), 7L);

    assertThat(result.passed()).isFalse();
    verify(graphClient, never()).evaluateAttempt(anyString(), anyString(), anyList(), anyInt());
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  @DisplayName("教师审核前的 AI 题可练习，但不计入 KC 掌握证据")
  void unapprovedAiProblemDoesNotPublishMasteryEvidence() {
    when(pregenService.findByProblemId(7L, "p1")).thenReturn(Optional.of(problemWithTests(3)));
    when(reviewRepository.isApproved(any())).thenReturn(false);
    when(graphClient.evaluateAttempt(eq("p1"), anyString(), anyList(), anyInt()))
        .thenReturn(new BossEvaluateResultDto("p1", true, 1, 1, null, null, List.of()));

    var result = service.evaluateAttempt(attemptRequest("p1", 1, 1, "print(5)", 10), 7L);

    assertThat(result.passed()).isTrue();
    assertThat(result.score()).isZero();
    assertThat(result.feedback()).contains("不计入本局进度、排行榜与知识画像");
    verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("教师撤销审核后的远征 AI 题保留结算证据，但不计入奖励")
  void revokedAiProblemRecordsSettlementEvidenceWithoutRewards() {
    String runId = "123e4567-e89b-12d3-a456-426614174000";
    BossInternalProblem problem = problemWithTests(1);
    when(pregenService.findExpeditionBinding(eq(7L), eq("p1")))
        .thenReturn(Optional.of(binding(runId, "L0-0", 1, 1, problem)));
    when(expeditionRunService.isActiveRun(7L, runId)).thenReturn(true);
    when(pregenService.findByProblemId(7L, "p1")).thenReturn(Optional.of(problem));
    when(reviewRepository.isApproved(problem)).thenReturn(false);
    when(expeditionScoreService.recordSettlementEvidence(
            eq(7L), eq(runId), eq("p1"), anyString(), eq(true)))
        .thenReturn(new ExpeditionScoreService.VerifiedAttemptResult(true, false));
    when(graphClient.evaluateAttempt(eq("p1"), anyString(), anyList(), anyInt()))
        .thenReturn(new BossEvaluateResultDto("p1", true, 1, 1, null, null, List.of()));

    var result = service.evaluateAttempt(attemptRequest("p1", 1, 1, "print(5)", 10), 7L);

    assertThat(result.passed()).isTrue();
    assertThat(result.score()).isZero();
    assertThat(result.feedback()).contains("不计入本局进度、排行榜与知识画像");
    verify(expeditionScoreService)
        .recordSettlementEvidence(eq(7L), eq(runId), eq("p1"), anyString(), eq(true));
    verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("attempt 未通过 → 发布 PracticeFailed（verdict=WA）")
  void failingAttemptPublishesPracticeFailed() {
    when(pregenService.findByProblemId(7L, "p1")).thenReturn(Optional.of(problemWithTests(2)));
    when(graphClient.evaluateAttempt(eq("p1"), anyString(), anyList(), anyInt()))
        .thenReturn(new BossEvaluateResultDto("p1", false, 0, 1, "hint", null, List.of(0)));

    service.evaluateAttempt(attemptRequest("p1", 1, 1, "print(0)", 10), 7L);

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

    service.evaluateAttempt(attemptRequest("p1", 1, 1, "print(x)", 10), 7L);

    ArgumentCaptor<LearningEvent.PracticeFailed> captor =
        ArgumentCaptor.forClass(LearningEvent.PracticeFailed.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().failureKind()).isEqualTo("RE");
  }

  @Test
  @DisplayName("题目缓存缺失（无判定）→ 不发任何学习事件")
  void missingProblemPublishesNothing() {
    String privateProblemId = "private-boss-missing-problem-sentinel";
    long privateUserId = 987654321L;
    when(pregenService.findByProblemId(privateUserId, privateProblemId))
        .thenReturn(Optional.empty());

    Logger logger = (Logger) LoggerFactory.getLogger(BossService.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.setLevel(Level.INFO);
    logger.addAppender(appender);
    try {
      service.evaluateAttempt(
          attemptRequest(privateProblemId, 1, 1, "print(1)", 10), privateUserId);

      assertThat(appender.list)
          .extracting(ILoggingEvent::getFormattedMessage)
          .allSatisfy(
              message ->
                  assertThat(message)
                      .doesNotContain(privateProblemId)
                      .doesNotContain(Long.toString(privateUserId)));
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }

    verify(attemptIdempotencyService, never()).claim(eq(privateUserId), any(), any());
    verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("Graph 报告沙箱不可用时使用本地沙箱；本地也不可用则不记分")
  void graphSandboxUnavailableDoesNotCountAsStudentFailure() {
    when(pregenService.findByProblemId(7L, "p1")).thenReturn(Optional.of(problemWithTests(3)));
    when(graphClient.evaluateAttempt(eq("p1"), anyString(), anyList(), anyInt()))
        .thenReturn(
            new BossEvaluateResultDto("p1", false, 0, 1, null, "sandbox_unavailable", List.of(0)));
    when(judgeWorkerPool.submitAndWait(any()))
        .thenAnswer(invocation -> ((Callable<?>) invocation.getArgument(0)).call());
    when(staticCodeChecker.check("print(1)"))
        .thenReturn(new StaticCodeChecker.CheckResult(false, "SYSTEM_ERROR", true));

    assertThatThrownBy(
            () -> service.evaluateAttempt(attemptRequest("p1", 1, 1, "print(1)", 10), 7L))
        .isInstanceOfSatisfying(
            com.pythontopia.common.error.BusinessException.class,
            exception -> assertThat(exception.code()).isEqualTo("BOSS_EVALUATOR_UNAVAILABLE"));

    verifyNoInteractions(expeditionScoreService, eventPublisher);
  }

  @Test
  @DisplayName("Graph 与本地判题环境都不可用时返回服务错误，不记为答错")
  void graphUnavailablePublishesNothing() {
    when(pregenService.findByProblemId(7L, "p1")).thenReturn(Optional.of(problemWithTests(3)));
    when(graphClient.evaluateAttempt(eq("p1"), anyString(), anyList(), anyInt()))
        .thenThrow(new RestClientException("graph down"));
    when(judgeWorkerPool.submitAndWait(any()))
        .thenAnswer(invocation -> ((Callable<?>) invocation.getArgument(0)).call());
    when(staticCodeChecker.check("print(1)"))
        .thenReturn(new StaticCodeChecker.CheckResult(false, "SYSTEM_ERROR", true));

    assertThatThrownBy(
            () -> service.evaluateAttempt(attemptRequest("p1", 1, 1, "print(1)", 10), 7L))
        .isInstanceOfSatisfying(
            com.pythontopia.common.error.BusinessException.class,
            exception -> {
              assertThat(exception.code()).isEqualTo("BOSS_EVALUATOR_UNAVAILABLE");
              assertThat(exception.status())
                  .isEqualTo(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
            });

    verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("Graph 评测不可用时由同一 Python 沙箱本地判题并回流真实结果")
  void graphUnavailableFallsBackToLocalJudge() {
    String privateContent = "private-boss-evaluation-response-sentinel";
    when(pregenService.findByProblemId(7L, "p1")).thenReturn(Optional.of(problemWithTests(3)));
    when(graphClient.evaluateAttempt(eq("p1"), anyString(), anyList(), anyInt()))
        .thenThrow(new RestClientException(privateContent));
    when(judgeWorkerPool.submitAndWait(any()))
        .thenAnswer(invocation -> ((Callable<?>) invocation.getArgument(0)).call());
    when(staticCodeChecker.check("print(5)"))
        .thenReturn(new StaticCodeChecker.CheckResult(true, null, false));
    when(judgeCaseEvaluator.evaluate(eq("print(5)"), anyList()))
        .thenReturn(new JudgeCaseEvaluator.CaseEvaluation(Verdict.AC, 1, 2, null, null));

    Logger logger = (Logger) LoggerFactory.getLogger(BossService.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.setLevel(Level.WARN);
    logger.addAppender(appender);
    try {
      var result = service.evaluateAttempt(attemptRequest("p1", 1, 1, "print(5)", 10), 7L);

      assertThat(result.passed()).isTrue();
      assertThat(result.passedTestCases()).isEqualTo(1);
      assertThat(appender.list)
          .extracting(ILoggingEvent::getFormattedMessage)
          .allSatisfy(message -> assertThat(message).doesNotContain(privateContent));
      verify(eventPublisher).publishEvent(any(LearningEvent.PracticeSucceeded.class));
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }
  }

  @Test
  @DisplayName("匿名用户（userId=null）→ 不发学习事件（无法归因）")
  void anonymousUserPublishesNothing() {
    when(pregenService.findByProblemId(any(), eq("p1")))
        .thenReturn(Optional.of(problemWithTests(3)));
    when(graphClient.evaluateAttempt(eq("p1"), anyString(), anyList(), anyInt()))
        .thenReturn(new BossEvaluateResultDto("p1", true, 1, 1, null, null, List.of()));

    service.evaluateAttempt(attemptRequest("p1", 1, 1, "print(5)", 10), null);

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

  private static BossAttemptRequest attemptRequest(
      String problemId, int chapter, int stage, String code, int elapsedSec) {
    return new BossAttemptRequest(problemId, chapter, stage, code, elapsedSec, UUID.randomUUID());
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

  private static BossInternalProblem internalProblem() {
    return new BossInternalProblem(
        "p1",
        1,
        1,
        1,
        "PY43-KC-001",
        "statement",
        List.of(),
        "in",
        "out",
        1,
        true,
        true,
        List.of(),
        "");
  }

  /** 带 hidden testCases 的内部题：evaluateAttempt 才能越过「空 testCases」兜底进入真实判题路径。 */
  private static BossInternalProblem problemWithTests(int difficulty) {
    return new BossInternalProblem(
        "p1",
        1,
        1,
        difficulty,
        "PY43-KC-001",
        "statement",
        List.of(),
        "2 3",
        "5",
        1,
        true,
        true,
        List.of(new BossTestCasePayload("2 3", "5", false)),
        "ref");
  }

  private static EndlessPregenService.ExpeditionProblemBinding binding(
      String runId, String nodeId, int chapter, int stage, BossInternalProblem problem) {
    return new EndlessPregenService.ExpeditionProblemBinding(
        runId,
        nodeId,
        chapter,
        stage,
        problem.kcCode(),
        BossService.questionFingerprint(problem),
        BossService.bossPhaseFromProblemId(problem.problemId()));
  }

  private static BossInternalProblem problemWithStage(int stage) {
    return problemWithStageAndDifficulty(stage, 1);
  }

  private static BossInternalProblem problemWithStageAndDifficulty(int stage, int difficulty) {
    return new BossInternalProblem(
        "p1",
        1,
        stage,
        difficulty,
        "PY43-KC-001",
        "statement",
        List.of(),
        "2 3",
        "5",
        1,
        true,
        true,
        List.of(new BossTestCasePayload("2 3", "5", false)),
        "ref");
  }

  private static BossInternalProblem problemWithStageAndDifficulty(int stage, int difficulty) {
    return new BossInternalProblem(
        "p1", 1, stage, difficulty, "PY43-KC-001", "statement", List.of(), "2 3", "5", 1, true,
        true, List.of(new BossTestCasePayload("2 3", "5", false)), "ref");
  }

  private static BossInternalProblem problemWithInput(String input) {
    return problemWithInput(input, 1);
  }

  private static BossInternalProblem problemWithInput(String input, int stage) {
    return problemWithInputAndDifficulty(input, stage, 1);
  }

  private static BossInternalProblem problemWithInputAndDifficulty(
      String input, int stage, int difficulty) {
    return new BossInternalProblem(
        "same-graph-id",
        1,
        stage,
        difficulty,
        "PY43-KC-001",
        "statement",
        List.of(),
        "",
        "",
        1,
        true,
        true,
        List.of(new BossTestCasePayload(input, "expected", false)),
        "ref");
  }

  private static BossInternalProblem problemWithInputAndKc(String input, String kcCode) {
    return new BossInternalProblem(
        "same-graph-id",
        1,
        1,
        1,
        kcCode,
        "statement",
        List.of(),
        "",
        "",
        1,
        true,
        true,
        List.of(new BossTestCasePayload(input, "expected", false)),
        "ref");
  }
}
