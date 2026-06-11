package com.pythontopia.mainline.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.mainline.domain.ChapterStar;
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
import com.pythontopia.mainline.test.ChapterTestJudge.QuestionJudgement;
import com.pythontopia.mainline.test.question.QuestionType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** ChapterTestService：prepare 幂等 / start golden 兜底与再进入 / submit 判分+超时拒收+幂等 / latest。 */
class ChapterTestServiceTest {

  private static final long USER = 7L;
  private static final int CH = 2;
  private static final long TEST_ID = 100L;

  private final ObjectMapper mapper = new ObjectMapper();
  private final ChapterTestRepository repo = mock(ChapterTestRepository.class);
  private final ChapterTestPregenService pregen = mock(ChapterTestPregenService.class);
  private final GoldenPaperProvider golden = mock(GoldenPaperProvider.class);
  private final ChapterTestJudge judge = mock(ChapterTestJudge.class);
  private final ChapterTestMapper mapperBean = new ChapterTestMapper();
  private final ChapterStarService starService = mock(ChapterStarService.class);
  private final ChapterStarRepository starRepo = mock(ChapterStarRepository.class);

  private final ChapterTestService service =
      new ChapterTestService(
          repo, pregen, golden, judge, mapperBean, starService, starRepo, mapper);

  private JsonNode questions() {
    try {
      return mapper.readTree(
          "{\"items\":[{\"qid\":\"c1\",\"type\":\"CHOICE\",\"stem\":\"s\","
              + "\"options\":[\"A. x\",\"B. y\"],\"answer\":\"A\",\"explain\":\"e\",\"score\":8}]}");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private ChapterTestRecord record(
      ChapterTestStatus status, OffsetDateTime deadline, JsonNode questions, JsonNode detail) {
    return new ChapterTestRecord(
        TEST_ID, USER, CH, status, 1, questions, null, null, 1800,
        deadline == null ? null : deadline.minusMinutes(30), deadline, null,
        detail == null ? null : detail.path("score").asInt(), detail,
        mapper.createObjectNode().put("paste_blocked", 0).put("blur_events", 0).put("overtime", false),
        OffsetDateTime.now());
  }

  // --- prepare ---------------------------------------------------------------

  @Test
  void prepareIsIdempotentWhenAlreadyReady() {
    when(repo.findLatest(USER, CH))
        .thenReturn(Optional.of(record(ChapterTestStatus.READY, null, questions(), null)));

    TestPrepareResponse resp = service.prepare(USER, CH);

    assertThat(resp.status()).isEqualTo("READY");
    verify(pregen, never()).pregenerate(anyLong(), anyLong(), anyInt());
    verify(repo, never()).insertPending(anyLong(), anyInt(), anyInt(), anyInt());
  }

  @Test
  void prepareTriggersAsyncGenerationWhenNone() {
    when(repo.findLatest(USER, CH)).thenReturn(Optional.empty());
    when(repo.nextAttemptNo(USER, CH)).thenReturn(1);
    when(repo.insertPending(USER, CH, 1, 1800)).thenReturn(TEST_ID);

    TestPrepareResponse resp = service.prepare(USER, CH);

    assertThat(resp.status()).isEqualTo("GENERATING");
    verify(repo).markStatus(TEST_ID, ChapterTestStatus.GENERATING);
    verify(pregen).pregenerate(TEST_ID, USER, CH);
  }

  // --- start -----------------------------------------------------------------

  @Test
  void startReentersExistingInProgressPaperStrippingAnswers() {
    when(repo.findLatest(USER, CH))
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
    verify(repo, never()).insertPending(anyLong(), anyInt(), anyInt(), anyInt());
    verify(repo, never()).startExam(anyLong(), any(), any());
  }

  @Test
  void startUsesGoldenFallbackWhenNotReady() {
    when(repo.findLatest(USER, CH))
        .thenReturn(Optional.of(record(ChapterTestStatus.PENDING, null, null, null)));
    when(repo.nextAttemptNo(USER, CH)).thenReturn(2);
    when(repo.insertPending(USER, CH, 2, 1800)).thenReturn(200L);
    when(golden.loadQuestions(CH)).thenReturn(questions());
    when(golden.meta(CH)).thenReturn(mapper.createObjectNode().put("source", "golden"));
    ChapterTestRecord ready =
        new ChapterTestRecord(
            200L, USER, CH, ChapterTestStatus.READY, 2, questions(), null, null, 1800,
            null, null, null, null, null, mapper.createObjectNode(), OffsetDateTime.now());
    ChapterTestRecord inProgress =
        new ChapterTestRecord(
            200L, USER, CH, ChapterTestStatus.IN_PROGRESS, 2, questions(), null, null, 1800,
            OffsetDateTime.now(), OffsetDateTime.now().plusMinutes(30), null, null, null,
            mapper.createObjectNode(), OffsetDateTime.now());
    when(repo.findById(200L)).thenReturn(Optional.of(ready), Optional.of(inProgress));
    when(repo.startExam(eq(200L), any(), any())).thenReturn(true);

    ChapterTestPaperDto paper = service.start(USER, CH);

    assertThat(paper.testId()).isEqualTo(200L);
    assertThat(paper.status()).isEqualTo("IN_PROGRESS");
    verify(golden).loadQuestions(CH);
    verify(repo).markReady(eq(200L), any(), any(), any());
    verify(repo).startExam(eq(200L), any(), any());
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

    ChapterTestSubmitRequest req =
        new ChapterTestSubmitRequest(
            Map.of("c1", "A"), new ChapterTestSubmitRequest.IntegrityReport(2, 1, false));

    assertThatThrownBy(() -> service.submit(USER, CH, TEST_ID, req))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("超过考试时限");

    verify(repo).markExpiredOvertime(eq(TEST_ID), any());
    verify(judge, never()).judge(any(), any());
    verify(repo, never()).markSubmitted(anyLong(), anyInt(), any(), any(), any());
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
    when(repo.markSubmitted(eq(TEST_ID), eq(80), any(), any(), any())).thenReturn(true);
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
    verify(repo).markSubmitted(eq(TEST_ID), eq(80), any(), any(), any());
    verify(starService).recalc(USER, CH, StarSource.TEST);
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
    verify(repo, never()).markSubmitted(anyLong(), anyInt(), any(), any(), any());
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

  // --- latest ----------------------------------------------------------------

  @Test
  void latestReturnsNullWhenNoTest() {
    when(repo.findLatest(USER, CH)).thenReturn(Optional.empty());
    assertThat(service.latest(USER, CH)).isNull();
  }

  @Test
  void latestRebuildsTerminalResultFromStoredDetail() throws Exception {
    JsonNode detail =
        mapper.readTree(
            "{\"score\":72,\"maxScore\":100,\"items\":[{\"qid\":\"c1\",\"type\":\"CHOICE\","
                + "\"correct\":true,\"awardedScore\":8,\"maxScore\":8}]}");
    when(repo.findLatest(USER, CH))
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
