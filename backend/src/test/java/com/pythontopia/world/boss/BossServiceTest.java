package com.pythontopia.world.boss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pythontopia.coursepack.domain.CourseKc;
import com.pythontopia.coursepack.repository.CoursePackRepository;
import com.pythontopia.learner.mastery.MasteryRepository;
import com.pythontopia.learner.mastery.MasterySnapshot;
import com.pythontopia.world.boss.dto.BossInternalProblem;
import com.pythontopia.world.boss.dto.BossProblemRequest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** P3.2：Boss 出题难度据该章 KC 真实掌握度 + 置信度推断（学情驱动），而非仅按 stage。 */
@ExtendWith(MockitoExtension.class)
class BossServiceTest {

  private static final Long CHAPTER_ID = 11L;

  @Mock BossGraphClient graphClient;
  @Mock MasteryRepository masteryRepository;
  @Mock CoursePackRepository coursePackRepository;
  @Mock EndlessPregenService pregenService;
  private BossService service;

  @BeforeEach
  void setUp() {
    service = new BossService(graphClient, masteryRepository, coursePackRepository, pregenService);
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
}
