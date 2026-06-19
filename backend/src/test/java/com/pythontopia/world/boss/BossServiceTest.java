package com.pythontopia.world.boss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pythontopia.coursepack.domain.CourseKc;
import com.pythontopia.coursepack.repository.CoursePackRepository;
import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.learner.mastery.MasteryRepository;
import com.pythontopia.learner.mastery.MasterySnapshot;
import com.pythontopia.world.boss.dto.BossAttemptRequest;
import com.pythontopia.world.boss.dto.BossEvaluateResultDto;
import com.pythontopia.world.boss.dto.BossInternalProblem;
import com.pythontopia.world.boss.dto.BossProblemRequest;
import com.pythontopia.world.boss.dto.BossTestCasePayload;
import java.math.BigDecimal;
import java.util.List;
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
  @Mock EndlessPregenService pregenService;
  @Mock ApplicationEventPublisher eventPublisher;
  private BossService service;

  @BeforeEach
  void setUp() {
    service =
        new BossService(
            graphClient, masteryRepository, coursePackRepository, pregenService, eventPublisher);
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
  @DisplayName("低掌握章节 → Boss 难度比 stage 低（学情驱动）")
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
        1, new BossProblemRequest(4, null, null, null, null, null, null), 7L);

    int difficulty = capturedDifficulty();
    assertThat(difficulty).isEqualTo(1);
    assertThat(difficulty).isLessThan(4);
  }

  @Test
  @DisplayName("高掌握+高置信章节 → Boss 难度更高")
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
        1, new BossProblemRequest(2, null, null, null, null, null, null), 7L);

    assertThat(capturedDifficulty()).isEqualTo(5);
  }

  @Test
  @DisplayName("前端显式 difficulty 覆盖学情推断（不读掌握度）")
  void explicitDifficultyOverridesMastery() {
    when(graphClient.requestProblem(
            anyLong(), anyInt(), anyInt(), anyString(), anyInt(), any(), any(), any()))
        .thenReturn(internalProblem());

    service.fetchProblem(
        1, new BossProblemRequest(2, null, 3, null, null, null, null), 7L);

    assertThat(capturedDifficulty()).isEqualTo(3);
    verifyNoInteractions(masteryRepository);
    verifyNoInteractions(coursePackRepository);
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
    // 显式 override 优先于默认锚定。
    assertThat(service.resolveKc(3, "PY43-KC-099")).isEqualTo("PY43-KC-099");
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

  /** 带 hidden testCases 的内部题：evaluateAttempt 才能越过「空 testCases」兜底进入真实判题路径。 */
  private static BossInternalProblem problemWithTests(int difficulty) {
    return new BossInternalProblem(
        "p1", 1, 1, difficulty, "PY43-KC-001", "statement", List.of(), "2 3", "5", 1, true, true,
        List.of(new BossTestCasePayload("2 3", "5", false)), "ref");
  }
}
