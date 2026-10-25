package com.pythontopia.mainline.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.mainline.MainlineStageProgressRepository;
import com.pythontopia.mainline.MainlineStageRepository;
import com.pythontopia.mainline.domain.ChapterAssessmentKind;
import com.pythontopia.mainline.domain.ChapterStar;
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
import com.pythontopia.mainline.test.question.QuestionType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** ChapterTestService：小测/大测 prepare / start / submit / latest 编排。 */
class ChapterTestServiceTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  private static final long USER = 7L;
  private static final int CH = 2;
  private static final long TEST_ID = 100L;

  private final ObjectMapper mapper = new ObjectMapper();
  private final ChapterTestRepository repo = mock(ChapterTestRepository.class);
  private final ChapterTestPregenService pregen = mock(ChapterTestPregenService.class);
  private final ChapterTestJudge judge = mock(ChapterTestJudge.class);
  private final ChapterTestMapper mapperBean = new ChapterTestMapper();
  private final ChapterStarService starService = mock(ChapterStarService.class);
  private final ChapterStarRepository starRepo = mock(ChapterStarRepository.class);
  private final MainlineStageRepository stageRepo = mock(MainlineStageRepository.class);
  private final MainlineStageProgressRepository progressRepo =
      mock(MainlineStageProgressRepository.class);
  private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
  private final TestTransactionManager transactionManager = new TestTransactionManager();

  private final ChapterTestService service =
      new ChapterTestService(
          repo,
          pregen,
          judge,
          mapperBean,
          starService,
          starRepo,
          stageRepo,
          progressRepo,
          mapper,
          eventPublisher,
          transactionManager);

  @BeforeEach
  void setUp() {
    transactionManager.reset();
    lenient().when(repo.claimSubmission(anyLong(), any(), any())).thenReturn(true);
    stubChapterStagesComplete();
    stubPreviousChapterUnlocked();
  }

  @Test
  void sandboxRunsWithoutTransactionAndResultPersistenceUsesShortTransaction() {
    AtomicBoolean transactionDuringJudge = new AtomicBoolean(true);
    AtomicBoolean transactionDuringPersist = new AtomicBoolean(false);
    when(repo.findOwned(USER, TEST_ID))
        .thenReturn(
            Optional.of(
                record(
                    ChapterTestStatus.IN_PROGRESS,
                    OffsetDateTime.now().plusMinutes(20),
                    questions(),
                    null)));
    when(judge.judge(any(), any()))
        .thenAnswer(
            invocation -> {
              transactionDuringJudge.set(
                  TransactionSynchronizationManager.isActualTransactionActive());
              return new JudgeReport(80, 100, List.of());
            });
    when(repo.markSubmitted(eq(TEST_ID), any(), eq(80), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              transactionDuringPersist.set(
                  TransactionSynchronizationManager.isActualTransactionActive());
              return true;
            });
    when(repo.findById(TEST_ID))
        .thenReturn(
            Optional.of(record(ChapterTestStatus.SUBMITTED, OffsetDateTime.now(), questions(), null)));
    when(starService.recalc(USER, CH, StarSource.TEST))
        .thenReturn(new ChapterStar(1L, USER, CH, 2, 80, mapper.createObjectNode(), null));

    ProxyFactory proxyFactory = new ProxyFactory(service);
    proxyFactory.addAdvice(
        new TransactionInterceptor(
            transactionManager, new AnnotationTransactionAttributeSource()));
    ChapterTestService transactionalService = (ChapterTestService) proxyFactory.getProxy();
    transactionalService.submit(USER, CH, TEST_ID, new ChapterTestSubmitRequest(Map.of(), null));

    assertThat(transactionDuringJudge).isFalse();
    assertThat(transactionDuringPersist).isTrue();
  }

  @Test
  void overtimeStatusCommitsBeforeConflictIsReturned() {
    when(repo.findOwned(USER, TEST_ID))
        .thenReturn(
            Optional.of(
                record(
                    ChapterTestStatus.IN_PROGRESS,
                    OffsetDateTime.now().minusMinutes(5),
                    questions(),
                    null)));
    when(repo.markExpiredOvertime(eq(TEST_ID), any(), any())).thenReturn(true);

    assertThatThrownBy(
            () ->
                service.submit(
                    USER,
                    CH,
                    TEST_ID,
                    new ChapterTestSubmitRequest(Map.of(), null)))
        .isInstanceOf(BusinessException.class);

    verify(repo).markExpiredOvertime(eq(TEST_ID), any(), any());
    assertThat(transactionManager.commitCount()).isEqualTo(1);
  }

  /** CH=2：开考需前一章 ≥1★，默认放行；锁章路径另有专测。 */
  private void stubPreviousChapterUnlocked() {
    when(starRepo.find(USER, CH - 1))
        .thenReturn(
            Optional.of(new ChapterStar(9L, USER, CH - 1, 1, 70, mapper.createObjectNode(), null)));
  }

  private JsonNode questions() {
    try {
      return mapper.readTree(
          "{\"items\":[{\"qid\":\"c1\",\"type\":\"CHOICE\",\"stem\":\"s\","
              + "\"options\":[\"A. x\",\"B. y\"],\"answer\":\"A\",\"explain\":\"e\",\"score\":8}]}");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static final class TestTransactionManager extends AbstractPlatformTransactionManager {
    private final AtomicInteger commits = new AtomicInteger();

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(
        Object transaction, org.springframework.transaction.TransactionDefinition definition) {}

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
      commits.incrementAndGet();
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {}

    void reset() {
      commits.set(0);
    }

    int commitCount() {
      return commits.get();
    }
  }

  private JsonNode questionsForAllTypes() {
    try {
      return mapper.readTree(
          """
          {
            "items": [
              {
                "qid": "code1",
                "type": "CODING",
                "title": "one",
                "statement_markdown": "one",
                "kc_code": "PY43-KC-001",
                "score": 30
              },
              {
                "qid": "code2",
                "type": "CODING",
                "title": "two",
                "statement_markdown": "two",
                "kc_code": "PY43-KC-001",
                "score": 30
              },
              {
                "qid": "choice1",
                "type": "CHOICE",
                "stem": "s",
                "options": ["A", "B"],
                "answer": "A",
                "kc_code": "PY43-KC-002",
                "score": 8
              },
              {
                "qid": "judge1",
                "type": "JUDGE",
                "answer": true,
                "kc_code": "PY43-KC-003",
                "score": 5
              },
              {
                "qid": "fill1",
                "type": "FILL",
                "answers": ["len"],
                "kc_code": "PY43-KC-004",
                "score": 5
              }
            ]
          }
          """);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private ChapterTestRecord record(
      ChapterTestStatus status, OffsetDateTime deadline, JsonNode questions, JsonNode detail) {
    return record(ChapterAssessmentKind.FINAL, status, deadline, questions, detail);
  }

  private ChapterTestRecord record(
      ChapterAssessmentKind assessmentKind,
      ChapterTestStatus status,
      OffsetDateTime deadline,
      JsonNode questions,
      JsonNode detail) {
    return new ChapterTestRecord(
        TEST_ID, USER, CH, assessmentKind, status, 1, questions, null, null, 7200,
        deadline == null ? null : deadline.minusMinutes(30), deadline, null,
        detail == null ? null : detail.path("score").asInt(), detail,
        mapper.createObjectNode().put("paste_blocked", 0).put("blur_events", 0).put("overtime", false),
        OffsetDateTime.now());
  }

  private MainlineStage stage(long id, int index) {
    return new MainlineStage(
        id, CH, index, "stage-" + index, List.of("PY43-KC-001"), "kw", List.of(), List.of(), 240, 2, 0);
  }

  private MainlineStageProgress completedProgress(long stageId) {
    return new MainlineStageProgress(
        stageId, USER, stageId, 240, List.of(1L, 2L), List.of(), List.of(), OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private void stubChapterStagesComplete() {
    when(stageRepo.findByChapter(CH)).thenReturn(List.of(stage(21L, 1), stage(22L, 2)));
    when(progressRepo.findByUser(USER))
        .thenReturn(List.of(completedProgress(21L), completedProgress(22L)));
  }

  // --- prepare ---------------------------------------------------------------

  @Test
  void prepareIsIdempotentWhenAlreadyReady() {
    when(repo.findLatest(USER, CH, ChapterAssessmentKind.FINAL))
        .thenReturn(Optional.of(record(ChapterTestStatus.READY, null, questions(), null)));

    TestPrepareResponse resp = service.prepare(USER, CH);

    assertThat(resp.status()).isEqualTo("READY");
    verify(pregen, never())
        .pregenerate(anyLong(), anyLong(), anyInt(), any(), anyList());
    verify(repo, never()).insertPending(anyLong(), anyInt(), any(), anyInt(), anyInt());
  }

  @Test
  void prepareTriggersAsyncGenerationWhenNone() {
    when(repo.findLatest(USER, CH, ChapterAssessmentKind.FINAL)).thenReturn(Optional.empty());
    when(repo.findLatest(USER, CH, ChapterAssessmentKind.QUIZ)).thenReturn(Optional.empty());
    when(repo.nextAttemptNo(USER, CH, ChapterAssessmentKind.FINAL)).thenReturn(1);
    when(repo.insertPending(USER, CH, ChapterAssessmentKind.FINAL, 1, 7200))
        .thenReturn(TEST_ID);

    TestPrepareResponse resp = service.prepare(USER, CH);

    assertThat(resp.status()).isEqualTo("GENERATING");
    verify(repo).markStatus(TEST_ID, ChapterTestStatus.GENERATING);
    verify(pregen)
        .pregenerate(TEST_ID, USER, CH, ChapterAssessmentKind.FINAL, List.of());
  }

  @Test
  void prepareMarksAttemptFailedWhenAsyncQueueIsFull() {
    when(repo.findLatest(USER, CH, ChapterAssessmentKind.FINAL)).thenReturn(Optional.empty());
    when(repo.findLatest(USER, CH, ChapterAssessmentKind.QUIZ)).thenReturn(Optional.empty());
    when(repo.nextAttemptNo(USER, CH, ChapterAssessmentKind.FINAL)).thenReturn(1);
    when(repo.insertPending(USER, CH, ChapterAssessmentKind.FINAL, 1, 7200))
        .thenReturn(TEST_ID);
    doThrow(new TaskRejectedException("queue full"))
        .when(pregen)
        .pregenerate(TEST_ID, USER, CH, ChapterAssessmentKind.FINAL, List.of());

    TestPrepareResponse response = service.prepare(USER, CH);

    assertThat(response.status()).isEqualTo("FAILED");
    verify(repo).markStatus(TEST_ID, ChapterTestStatus.FAILED);
  }

  @Test
  void prepareQuizUsesThirtyThreeQuestionsAndSixtyMinuteAttempt() {
    when(repo.findLatest(USER, CH, ChapterAssessmentKind.QUIZ)).thenReturn(Optional.empty());
    when(repo.nextAttemptNo(USER, CH, ChapterAssessmentKind.QUIZ)).thenReturn(1);
    when(repo.insertPending(USER, CH, ChapterAssessmentKind.QUIZ, 1, 3600))
        .thenReturn(TEST_ID);

    TestPrepareResponse resp = service.prepareQuiz(USER, CH);

    assertThat(resp.status()).isEqualTo("GENERATING");
    verify(pregen)
        .pregenerate(TEST_ID, USER, CH, ChapterAssessmentKind.QUIZ, List.of());
  }

  @Test
  void prepareFinalExcludesQuestionsFromLatestQuiz() {
    when(repo.findLatest(USER, CH, ChapterAssessmentKind.FINAL)).thenReturn(Optional.empty());
    when(repo.findLatest(USER, CH, ChapterAssessmentKind.QUIZ))
        .thenReturn(
            Optional.of(
                record(
                    ChapterAssessmentKind.QUIZ,
                    ChapterTestStatus.SUBMITTED,
                    null,
                    questions(),
                    null)));
    when(repo.nextAttemptNo(USER, CH, ChapterAssessmentKind.FINAL)).thenReturn(2);
    when(repo.insertPending(USER, CH, ChapterAssessmentKind.FINAL, 2, 7200))
        .thenReturn(TEST_ID);

    service.prepare(USER, CH);

    verify(pregen)
        .pregenerate(
            TEST_ID, USER, CH, ChapterAssessmentKind.FINAL, List.of("s"));
  }

  /** 已超时却仍挂 IN_PROGRESS：prepare 不能回「继续考」，必须作废旧场次并重新生成。 */
  @Test
  void prepareRestartsWhenInProgressAttemptIsPastDeadline() {
    when(repo.findLatest(USER, CH, ChapterAssessmentKind.FINAL))
        .thenReturn(
            Optional.of(
                record(
                    ChapterTestStatus.IN_PROGRESS,
                    OffsetDateTime.now().minusMinutes(5), // 已过 deadline
                    questions(),
                    null)));
    when(repo.findLatest(USER, CH, ChapterAssessmentKind.QUIZ)).thenReturn(Optional.empty());
    when(repo.nextAttemptNo(USER, CH, ChapterAssessmentKind.FINAL)).thenReturn(2);
    when(repo.insertPending(USER, CH, ChapterAssessmentKind.FINAL, 2, 7200)).thenReturn(101L);

    TestPrepareResponse resp = service.prepare(USER, CH);

    assertThat(resp.status()).isEqualTo("GENERATING");
    verify(repo).markStatus(TEST_ID, ChapterTestStatus.EXPIRED); // 旧场次作废
    verify(pregen).pregenerate(101L, USER, CH, ChapterAssessmentKind.FINAL, List.of());
  }

  /** 未超时的 IN_PROGRESS 仍应幂等复用（不误伤正常续考）。 */
  @Test
  void prepareKeepsLiveInProgressAttempt() {
    when(repo.findLatest(USER, CH, ChapterAssessmentKind.FINAL))
        .thenReturn(
            Optional.of(
                record(
                    ChapterTestStatus.IN_PROGRESS,
                    OffsetDateTime.now().plusMinutes(20),
                    questions(),
                    null)));

    assertThat(service.prepare(USER, CH).status()).isEqualTo("IN_PROGRESS");
    verify(repo, never()).insertPending(anyLong(), anyInt(), any(), anyInt(), anyInt());
    verify(repo, never()).markStatus(anyLong(), any());
  }

  // --- start -----------------------------------------------------------------

  @Test
  void startReentersExistingInProgressPaperStrippingAnswers() {
    when(repo.findLatest(USER, CH, ChapterAssessmentKind.FINAL))
        .thenReturn(
            Optional.of(
                record(
                    ChapterTestStatus.IN_PROGRESS,
                    OffsetDateTime.now().plusMinutes(20),
                    questions(),
                    null)));

    ChapterTestPaperDto paper = service.start(USER, CH);

    assertThat(paper.items()).hasSize(1);
    assertThat(paper.items().get(0).type()).isEqualTo("CHOICE");
    assertThat(paper.items().get(0).options()).containsExactly("A. x", "B. y");
    // 去答案题面 DTO 结构上不含答案字段（QuestionView 无 answer 成员）。
    verify(repo, never()).insertPending(anyLong(), anyInt(), any(), anyInt(), anyInt());
    verify(repo, never()).startExam(anyLong(), any(), any());
  }

  @Test
  void startRejectsWhenPaperIsNotReady() {
    when(repo.findLatest(USER, CH, ChapterAssessmentKind.FINAL))
        .thenReturn(Optional.of(record(ChapterTestStatus.PENDING, null, null, null)));

    assertThatThrownBy(() -> service.start(USER, CH))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("仍在生成");

    verify(repo, never()).startExam(anyLong(), any(), any());
  }

  @Test
  void startFinalRejectsWhenChapterStagesAreNotCompleted() {
    when(progressRepo.findByUser(USER)).thenReturn(List.of(completedProgress(21L)));
    when(repo.findLatest(USER, CH, ChapterAssessmentKind.FINAL))
        .thenReturn(
            Optional.of(
                record(
                    ChapterTestStatus.READY,
                    null,
                    questions(),
                    null)));

    assertThatThrownBy(() -> service.start(USER, CH))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("先完成本章所有小节");

    verify(repo, never()).startExam(anyLong(), any(), any());
  }

  /** 过期场次开考应给「重新准备」而不是「题目仍在生成，请稍后重试」（后者让学生白等）。 */
  @Test
  void startExpiredAttemptTellsStudentToPrepareAgain() {
    when(repo.findLatest(USER, CH, ChapterAssessmentKind.FINAL))
        .thenReturn(
            Optional.of(record(ChapterTestStatus.EXPIRED, OffsetDateTime.now().minusHours(3),
                questions(), null)));

    assertThatThrownBy(() -> service.start(USER, CH))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("本场已超时，请重新准备");

    verify(repo, never()).startExam(anyLong(), any(), any());
  }

  /** 章解锁 gate：前一章 0★ 时不得开考（否则可直接跳到第 7 章）。 */
  @Test
  void startRejectsWhenPreviousChapterHasNoStar() {
    when(starRepo.find(USER, CH - 1)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.start(USER, CH))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("尚未解锁");

    verify(repo, never()).startExam(anyLong(), any(), any());
  }

  @Test
  void startQuizDoesNotRequireAllChapterStagesCompleted() {
    when(progressRepo.findByUser(USER)).thenReturn(List.of());
    when(repo.findLatest(USER, CH, ChapterAssessmentKind.QUIZ))
        .thenReturn(
            Optional.of(
                record(
                    ChapterAssessmentKind.QUIZ,
                    ChapterTestStatus.READY,
                    null,
                    questions(),
                    null)));
    when(repo.startExam(eq(TEST_ID), any(), any())).thenReturn(true);
    when(repo.findById(TEST_ID))
        .thenReturn(
            Optional.of(
                record(
                    ChapterAssessmentKind.QUIZ,
                    ChapterTestStatus.READY,
                    null,
                    questions(),
                    null)))
        .thenReturn(
            Optional.of(
                record(
                    ChapterAssessmentKind.QUIZ,
                    ChapterTestStatus.IN_PROGRESS,
                    OffsetDateTime.now().plusHours(1),
                    questions(),
                    null)));

    ChapterTestPaperDto paper = service.startQuiz(USER, CH);

    assertThat(paper.status()).isEqualTo("IN_PROGRESS");
    verify(repo).startExam(eq(TEST_ID), any(), any());
  }

  // --- submit ----------------------------------------------------------------

  @Test
  void submitRejectsOvertimeAndRecordsIntegrity() {
    when(repo.findOwned(USER, TEST_ID))
        .thenReturn(
            Optional.of(
                record(
                    ChapterTestStatus.IN_PROGRESS,
                    OffsetDateTime.now().minusMinutes(5), // 已过 deadline
                    questions(),
                    null)));
    when(repo.markExpiredOvertime(eq(TEST_ID), any(), any())).thenReturn(true);

    ChapterTestSubmitRequest req =
        new ChapterTestSubmitRequest(
            Map.of("c1", "A"), new ChapterTestSubmitRequest.IntegrityReport(2, 1, false));

    assertThatThrownBy(() -> service.submit(USER, CH, TEST_ID, req))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("超过考试时限");

    verify(repo).markExpiredOvertime(eq(TEST_ID), any(), any());
    verify(judge, never()).judge(any(), any());
    verify(repo, never()).markSubmitted(anyLong(), any(), anyInt(), any(), any(), any());
  }

  @Test
  void overtimeLosingRaceReturnsAlreadySubmittedResult() throws Exception {
    JsonNode detail =
        mapper.readTree(
            "{\"score\":80,\"maxScore\":100,\"items\":[{\"qid\":\"c1\",\"type\":\"CHOICE\","
                + "\"correct\":true,\"awardedScore\":8,\"maxScore\":8,\"yourAnswer\":\"A\","
                + "\"correctAnswer\":\"A\",\"explain\":\"e\"}]}");
    when(repo.findOwned(USER, TEST_ID))
        .thenReturn(
            Optional.of(
                record(
                    ChapterTestStatus.IN_PROGRESS,
                    OffsetDateTime.now().minusMinutes(5),
                    questions(),
                    null)),
            Optional.of(record(ChapterTestStatus.SUBMITTED, OffsetDateTime.now(), questions(), detail)));
    when(repo.markExpiredOvertime(eq(TEST_ID), any(), any())).thenReturn(false);
    when(starRepo.find(USER, CH))
        .thenReturn(Optional.of(new ChapterStar(1L, USER, CH, 2, 80, mapper.createObjectNode(), null)));

    ChapterTestResultDto result =
        service.submit(USER, CH, TEST_ID, new ChapterTestSubmitRequest(Map.of("c1", "A"), null));

    assertThat(result.status()).isEqualTo("SUBMITTED");
    assertThat(result.score()).isEqualTo(80);
    verify(judge, never()).judge(any(), any());
  }

  @Test
  void judgeFailureReleasesSubmissionClaimForRetry() {
    when(repo.findOwned(USER, TEST_ID))
        .thenReturn(
            Optional.of(
                record(
                    ChapterTestStatus.IN_PROGRESS,
                    OffsetDateTime.now().plusMinutes(20),
                    questions(),
                    null)));
    BusinessException unavailable =
        new BusinessException(
            org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
            "CHAPTER_TEST_JUDGE_UNAVAILABLE",
            "判题暂不可用");
    when(judge.judge(any(), any())).thenThrow(unavailable);

    assertThatThrownBy(
            () -> service.submit(USER, CH, TEST_ID, new ChapterTestSubmitRequest(Map.of(), null)))
        .isSameAs(unavailable);

    verify(repo).releaseSubmissionClaim(eq(TEST_ID), any());
    verify(repo, never()).markSubmitted(anyLong(), any(), anyInt(), any(), any(), any());
  }

  @Test
  void submitJudgesPersistsAndRecalculatesStars() {
    when(repo.findOwned(USER, TEST_ID))
        .thenReturn(
            Optional.of(
                record(
                    ChapterTestStatus.IN_PROGRESS,
                    OffsetDateTime.now().plusMinutes(20),
                    questions(),
                    null)));
    when(judge.judge(any(), any()))
        .thenReturn(
            new JudgeReport(
                80,
                100,
                List.of(
                    new QuestionJudgement(
                        "c1", QuestionType.CHOICE, true, 8, 8, "A", "A", "e", null, null))));
    when(repo.markSubmitted(eq(TEST_ID), any(), eq(80), any(), any(), any())).thenReturn(true);
    when(starService.recalc(USER, CH, StarSource.TEST))
        .thenReturn(new ChapterStar(1L, USER, CH, 2, 80, mapper.createObjectNode(), null));
    when(repo.findById(TEST_ID))
        .thenReturn(
            Optional.of(record(ChapterTestStatus.SUBMITTED, OffsetDateTime.now(), questions(), null)));

    ChapterTestResultDto result =
        service.submit(
            USER, CH, TEST_ID,
            new ChapterTestSubmitRequest(Map.of("c1", "A"), null));

    assertThat(result.score()).isEqualTo(80);
    assertThat(result.maxScore()).isEqualTo(100);
    assertThat(result.passed()).isTrue();
    assertThat(result.stars()).isEqualTo(2);
    assertThat(result.overtime()).isFalse();
    verify(repo).markSubmitted(eq(TEST_ID), any(), eq(80), any(), any(), any());
    verify(starService).recalc(USER, CH, StarSource.TEST);
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  void submitPublishesAssessmentEventsForEveryQuestionType() {
    JsonNode paper = questionsForAllTypes();
    when(repo.findOwned(USER, TEST_ID))
        .thenReturn(Optional.of(record(ChapterTestStatus.IN_PROGRESS,
            OffsetDateTime.now().plusMinutes(20), paper, null)));
    when(judge.judge(any(), any()))
        .thenReturn(new JudgeReport(43, 78, List.of(
            new QuestionJudgement("code1", QuestionType.CODING, true, 30, 30,
                "code", "reference", "passed", 2, 2),
            new QuestionJudgement("code2", QuestionType.CODING, false, 0, 30,
                "code", "reference", "failed", 0, 2),
            new QuestionJudgement("choice1", QuestionType.CHOICE, true, 8, 8,
                "A", "A", "correct", null, null),
            new QuestionJudgement("judge1", QuestionType.JUDGE, false, 0, 5,
                "true", "false", "incorrect", null, null),
            new QuestionJudgement("fill1", QuestionType.FILL, true, 5, 5,
                "len", "len", "correct", null, null))));
    when(repo.markSubmitted(eq(TEST_ID), any(), eq(43), any(), any(), any())).thenReturn(true);
    when(starService.recalc(USER, CH, StarSource.TEST))
        .thenReturn(new ChapterStar(1L, USER, CH, 2, 43, mapper.createObjectNode(), null));
    when(repo.findById(TEST_ID))
        .thenReturn(Optional.of(record(ChapterTestStatus.SUBMITTED, OffsetDateTime.now(), paper, null)));

    service.submit(USER, CH, TEST_ID, new ChapterTestSubmitRequest(Map.of(), null));

    ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
    verify(eventPublisher, times(4)).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getAllValues())
        .containsExactlyInAnyOrder(
            new LearningEvent.ChapterAssessmentScored(
                USER, TEST_ID, CH, "FINAL", "PY43-KC-001", 1, 2),
            new LearningEvent.ChapterAssessmentScored(
                USER, TEST_ID, CH, "FINAL", "PY43-KC-002", 1, 1),
            new LearningEvent.ChapterAssessmentScored(
                USER, TEST_ID, CH, "FINAL", "PY43-KC-003", 0, 1),
            new LearningEvent.ChapterAssessmentScored(
                USER, TEST_ID, CH, "FINAL", "PY43-KC-004", 1, 1));
  }

  @Test
  void submitIsIdempotentWhenAlreadySubmitted() throws Exception {
    JsonNode detail =
        mapper.readTree(
            "{\"score\":80,\"maxScore\":100,\"items\":[{\"qid\":\"c1\",\"type\":\"CHOICE\","
                + "\"correct\":true,\"awardedScore\":8,\"maxScore\":8,\"yourAnswer\":\"A\","
                + "\"correctAnswer\":\"A\",\"explain\":\"e\"}]}");
    when(repo.findOwned(USER, TEST_ID))
        .thenReturn(
            Optional.of(record(ChapterTestStatus.SUBMITTED, OffsetDateTime.now(), questions(), detail)));
    when(starRepo.find(USER, CH))
        .thenReturn(Optional.of(new ChapterStar(1L, USER, CH, 2, 80, mapper.createObjectNode(), null)));

    ChapterTestResultDto result =
        service.submit(
            USER, CH, TEST_ID, new ChapterTestSubmitRequest(Map.of("c1", "A"), null));

    assertThat(result.status()).isEqualTo("SUBMITTED");
    assertThat(result.score()).isEqualTo(80);
    assertThat(result.items()).hasSize(1);
    verify(judge, never()).judge(any(), any());
    verify(repo, never()).markSubmitted(anyLong(), any(), anyInt(), any(), any(), any());
  }

  @Test
  void submitRejectsWhenNotStarted() {
    when(repo.findOwned(USER, TEST_ID))
        .thenReturn(Optional.of(record(ChapterTestStatus.READY, null, questions(), null)));

    assertThatThrownBy(
            () ->
                service.submit(
                    USER, CH, TEST_ID, new ChapterTestSubmitRequest(Map.of(), null)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("先开考");
  }

  @Test
  void submitRejectsOversizedAnswerValuesAtTheRequestBoundary() {
    ChapterTestSubmitRequest request =
        new ChapterTestSubmitRequest(Map.of("coding", "x".repeat(8193)), null);

    assertThat(validator.validate(request)).isNotEmpty();
  }

  // --- latest ----------------------------------------------------------------

  @Test
  void latestReturnsNullWhenNoTest() {
    when(repo.findLatest(USER, CH, ChapterAssessmentKind.FINAL)).thenReturn(Optional.empty());
    assertThat(service.latest(USER, CH)).isNull();
  }

  @Test
  void latestRebuildsTerminalResultFromStoredDetail() throws Exception {
    JsonNode detail =
        mapper.readTree(
            "{\"score\":72,\"maxScore\":100,\"items\":[{\"qid\":\"c1\",\"type\":\"CHOICE\","
                + "\"correct\":true,\"awardedScore\":8,\"maxScore\":8}]}");
    when(repo.findLatest(USER, CH, ChapterAssessmentKind.FINAL))
        .thenReturn(
            Optional.of(record(ChapterTestStatus.SUBMITTED, OffsetDateTime.now(), questions(), detail)));
    lenient().when(starRepo.find(USER, CH))
        .thenReturn(Optional.of(new ChapterStar(1L, USER, CH, 1, 72, mapper.createObjectNode(), null)));

    ChapterTestResultDto result = service.latest(USER, CH);

    assertThat(result).isNotNull();
    assertThat(result.score()).isEqualTo(72);
    assertThat(result.items()).hasSize(1);
    assertThat(result.stars()).isEqualTo(1);
  }

}
