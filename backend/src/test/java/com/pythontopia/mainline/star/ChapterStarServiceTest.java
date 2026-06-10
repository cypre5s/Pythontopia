package com.pythontopia.mainline.star;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.pythontopia.mainline.MainlineStageProgressRepository;
import com.pythontopia.mainline.MainlineStageRepository;
import com.pythontopia.mainline.domain.ChapterStar;
import com.pythontopia.mainline.domain.MainlineStage;
import com.pythontopia.mainline.domain.MainlineStageProgress;
import com.pythontopia.mainline.test.ChapterTestRepository;
import com.pythontopia.learner.mastery.MasteryRepository;
import com.pythontopia.learner.mastery.MasterySnapshot;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** ChapterStarService.recalc：核心公式落库 + 幂等 + 只升不降 + 补星保留。 */
class ChapterStarServiceTest {

  private static final long USER = 7L;
  private static final int CH = 1;

  private final ObjectMapper mapper = new ObjectMapper();
  private final ChapterStarRepository starRepo = mock(ChapterStarRepository.class);
  private final MainlineStageRepository stageRepo = mock(MainlineStageRepository.class);
  private final MainlineStageProgressRepository progressRepo =
      mock(MainlineStageProgressRepository.class);
  private final ChapterTestRepository testRepo = mock(ChapterTestRepository.class);
  private final MasteryRepository masteryRepo = mock(MasteryRepository.class);
  private final ChapterStarService service =
      new ChapterStarService(starRepo, stageRepo, progressRepo, testRepo, masteryRepo, mapper);

  private MainlineStage stage(long id) {
    return new MainlineStage(
        id, CH, (int) id, "t", List.of("PY43-KC-001"), "kw", List.of(), List.of(), 240, 0, 2);
  }

  private MainlineStageProgress completed(long stageId) {
    return new MainlineStageProgress(
        stageId, USER, stageId, 240, List.of(), List.of(1L, 2L),
        OffsetDateTime.now(), OffsetDateTime.now());
  }

  private ChapterStar existingStar(int stars, String breakdownJson) throws Exception {
    return new ChapterStar(1L, USER, CH, stars, 70, mapper.readTree(breakdownJson), null);
  }

  private void stubAllStagesComplete() {
    when(stageRepo.findByChapter(CH)).thenReturn(List.of(stage(10), stage(11)));
    when(progressRepo.findByUser(USER)).thenReturn(List.of(completed(10), completed(11)));
  }

  private void stubLowMastery() {
    when(masteryRepo.findAll(USER))
        .thenReturn(List.of(new MasterySnapshot(1L, USER, "PY43-KC-001", new BigDecimal("0.5"),
            new BigDecimal("0.9"), null, null)));
  }

  @Test
  void awardsTwoStarsAndRecordsEventOnFirstPass() throws Exception {
    stubAllStagesComplete();
    stubLowMastery();
    when(testRepo.findBestScore(USER, CH)).thenReturn(Optional.of(70));
    when(starRepo.find(USER, CH))
        .thenReturn(Optional.empty()) // 重算前：无星
        .thenReturn(Optional.of(new ChapterStar(1L, USER, CH, 2, 70, mapper.createObjectNode(), null)));

    ChapterStar result = service.recalc(USER, CH, StarSource.TEST);

    assertThat(result.stars()).isEqualTo(2);
    ArgumentCaptor<Integer> stars = ArgumentCaptor.forClass(Integer.class);
    ArgumentCaptor<String> breakdown = ArgumentCaptor.forClass(String.class);
    verify(starRepo).upsert(eq(USER), eq(CH), stars.capture(), eq(70), breakdown.capture());
    assertThat(stars.getValue()).isEqualTo(2);
    JsonNode bd = mapper.readTree(breakdown.getValue());
    assertThat(bd.get("test").asInt()).isEqualTo(1);
    assertThat(bd.get("stage").asInt()).isEqualTo(1);
    assertThat(bd.get("mastery").asInt()).isZero();
    verify(starRepo).insertEvent(eq(USER), eq(CH), eq(2), eq("TEST"), any());
  }

  @Test
  void idempotentNoEventWhenStarsUnchanged() throws Exception {
    stubAllStagesComplete();
    stubLowMastery();
    when(testRepo.findBestScore(USER, CH)).thenReturn(Optional.of(70));
    // 既有已是 2★，重算仍 2★。
    when(starRepo.find(USER, CH))
        .thenReturn(Optional.of(existingStar(2, "{\"test\":1,\"stage\":1,\"mastery\":0,\"recovered\":[]}")));

    service.recalc(USER, CH, StarSource.TEST);

    verify(starRepo).upsert(eq(USER), eq(CH), eq(2), any(), any()); // 幂等写仍发生
    verify(starRepo, never()).insertEvent(anyLong(), anyInt(), anyInt(), any(), any());
  }

  @Test
  void onlyUpNeverDownKeepsHigherExistingStars() throws Exception {
    // 既有 3★；当前核心仅算出 1★（stage 未全完成）。结果仍保 3★，且不记事件。
    when(stageRepo.findByChapter(CH)).thenReturn(List.of(stage(10), stage(11)));
    when(progressRepo.findByUser(USER)).thenReturn(List.of()); // 无完成 → stage 门槛不过
    lenient().when(masteryRepo.findAll(USER)).thenReturn(List.of());
    when(testRepo.findBestScore(USER, CH)).thenReturn(Optional.of(60));
    when(starRepo.find(USER, CH))
        .thenReturn(Optional.of(existingStar(3, "{\"test\":1,\"stage\":1,\"mastery\":1,\"recovered\":[]}")));

    service.recalc(USER, CH, StarSource.MASTERY);

    verify(starRepo).upsert(eq(USER), eq(CH), eq(3), any(), any());
    verify(starRepo, never()).insertEvent(anyLong(), anyInt(), anyInt(), any(), any());
  }

  @Test
  void preservesRecoveredStarFromExistingBreakdown() throws Exception {
    stubAllStagesComplete();
    stubLowMastery();
    when(testRepo.findBestScore(USER, CH)).thenReturn(Optional.of(70)); // 核心 2★
    when(starRepo.find(USER, CH))
        .thenReturn(
            Optional.of(
                existingStar(3, "{\"test\":1,\"stage\":1,\"mastery\":0,\"recovered\":[\"ENDLESS_RECOVERY\"]}")));

    service.recalc(USER, CH, StarSource.STAGE_COMPLETE);

    ArgumentCaptor<String> breakdown = ArgumentCaptor.forClass(String.class);
    verify(starRepo).upsert(eq(USER), eq(CH), eq(3), any(), breakdown.capture());
    JsonNode bd = mapper.readTree(breakdown.getValue());
    assertThat(bd.get("recovered").get(0).asText()).isEqualTo("ENDLESS_RECOVERY");
    verify(starRepo, never()).insertEvent(anyLong(), anyInt(), anyInt(), any(), any());
  }
}
