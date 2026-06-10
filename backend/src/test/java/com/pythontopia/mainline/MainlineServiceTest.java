package com.pythontopia.mainline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.mainline.domain.ChapterStar;
import com.pythontopia.mainline.domain.MainlineStage;
import com.pythontopia.mainline.domain.MainlineStageProgress;
import com.pythontopia.mainline.dto.MainlineOverviewDto;
import com.pythontopia.mainline.dto.StageProgressDto;
import com.pythontopia.mainline.star.ChapterStarRepository;
import com.pythontopia.mainline.star.ChapterStarService;
import com.pythontopia.mainline.star.StarSource;
import com.pythontopia.mainline.test.ChapterTestRepository;
import com.pythontopia.submission.dto.SubmissionDto;
import com.pythontopia.submission.dto.SubmissionStatus;
import com.pythontopia.submission.repository.PracticeSubmissionRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** MainlineService：overview 单次聚合（avoid N+1）+ 打点幂等/限频/校验/完成触发重算。 */
class MainlineServiceTest {

  private static final long USER = 7L;

  private final ObjectMapper mapper = new ObjectMapper();
  private final MainlineStageRepository stageRepo = mock(MainlineStageRepository.class);
  private final MainlineStageProgressRepository progressRepo =
      mock(MainlineStageProgressRepository.class);
  private final ChapterStarRepository starRepo = mock(ChapterStarRepository.class);
  private final ChapterTestRepository testRepo = mock(ChapterTestRepository.class);
  private final ChapterStarService starService = mock(ChapterStarService.class);
  private final VideoProgressThrottle throttle = mock(VideoProgressThrottle.class);
  private final PracticeSubmissionRepository submissionRepo =
      mock(PracticeSubmissionRepository.class);
  private final MainlineProperties properties =
      new MainlineProperties(null, null, null, null); // 默认：deltaMax=120, writesPerMin=2

  private final MainlineService service =
      new MainlineService(
          stageRepo, progressRepo, starRepo, testRepo, starService, properties, throttle,
          submissionRepo, mapper);

  private MainlineStage stage(long id, int chapter) {
    return new MainlineStage(
        id, chapter, 1, "s" + id, List.of("PY43-KC-001"), "kw", List.of(), List.of(), 240, 0, 2);
  }

  private MainlineStageProgress progress(
      long stageId, int videoSec, List<Long> examples, OffsetDateTime completedAt) {
    return new MainlineStageProgress(
        stageId, USER, stageId, videoSec, List.of(), examples, completedAt, OffsetDateTime.now());
  }

  @Test
  void overviewAggregatesInSingleQueriesAvoidingNPlusOne() throws Exception {
    when(stageRepo.findAll()).thenReturn(List.of(stage(10, 1), stage(20, 2)));
    when(progressRepo.findByUser(USER))
        .thenReturn(List.of(progress(10, 240, List.of(1L, 2L), OffsetDateTime.now())));
    when(starRepo.findByUser(USER))
        .thenReturn(
            List.of(
                new ChapterStar(
                    1L, USER, 1, 2, 70,
                    mapper.readTree(
                        "{\"test\":1,\"stage\":1,\"mastery\":0,\"recovered\":[\"ERRORBOOK_RECOVERY\"]}"),
                    null)));
    when(testRepo.findLatestStatusByChapter(USER)).thenReturn(Map.of(1, "SUBMITTED"));

    MainlineOverviewDto overview = service.overview(USER);

    assertThat(overview.totalStars()).isEqualTo(2);
    assertThat(overview.chapters()).hasSize(7);

    var ch1 = overview.chapters().get(0);
    assertThat(ch1.chapter()).isEqualTo(1);
    assertThat(ch1.unlocked()).isTrue();
    assertThat(ch1.stars()).isEqualTo(2);
    assertThat(ch1.bestTestScore()).isEqualTo(70);
    assertThat(ch1.testStatus()).isEqualTo("SUBMITTED");
    assertThat(ch1.recovery().errorbookDone()).isTrue();
    assertThat(ch1.stages()).hasSize(1);
    assertThat(ch1.stages().get(0).completed()).isTrue();

    var ch2 = overview.chapters().get(1);
    assertThat(ch2.unlocked()).isTrue(); // 前章 2★ ≥1 → 解锁
    assertThat(ch2.stars()).isZero();
    assertThat(ch2.testStatus()).isEqualTo("NONE");

    assertThat(overview.chapters().get(2).unlocked()).isFalse(); // 前章 0★ → 锁定

    // avoid N+1：每类数据各查询一次，不在章/stage 维度循环查询。
    verify(stageRepo, times(1)).findAll();
    verify(progressRepo, times(1)).findByUser(USER);
    verify(starRepo, times(1)).findByUser(USER);
    verify(testRepo, times(1)).findLatestStatusByChapter(USER);
  }

  @Test
  void videoProgressThrottledIsNoOp() {
    when(stageRepo.findById(10L)).thenReturn(Optional.of(stage(10, 1)));
    when(throttle.tryAcquire(USER, 10L)).thenReturn(false);
    when(progressRepo.find(USER, 10L))
        .thenReturn(Optional.of(progress(10, 100, List.of(), null)));

    StageProgressDto dto = service.reportVideoProgress(USER, 10L, 30);

    assertThat(dto.videoWatchedSec()).isEqualTo(100); // 未累加
    verify(progressRepo, never()).addVideoSeconds(anyLong(), anyLong(), anyInt());
  }

  @Test
  void videoProgressAcceptedClampsAndAccumulates() {
    when(stageRepo.findById(10L)).thenReturn(Optional.of(stage(10, 1)));
    when(throttle.tryAcquire(USER, 10L)).thenReturn(true);
    when(progressRepo.addVideoSeconds(eq(USER), eq(10L), eq(120)))
        .thenReturn(progress(10, 120, List.of(), null));

    StageProgressDto dto = service.reportVideoProgress(USER, 10L, 9999); // 超上限 → 钳制到 120

    assertThat(dto.videoWatchedSec()).isEqualTo(120);
    assertThat(dto.completed()).isFalse();
    verify(progressRepo).addVideoSeconds(USER, 10L, 120);
    verify(starService, never()).recalc(anyLong(), anyInt(), any());
  }

  @Test
  void exampleDoneRejectsMissingOrUnpassedSubmission() {
    when(stageRepo.findById(10L)).thenReturn(Optional.of(stage(10, 1)));
    when(submissionRepo.findSubmission(USER, 55L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.reportExampleDone(USER, 10L, 99L, 55L))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("不属于本人");

    when(submissionRepo.findSubmission(USER, 56L))
        .thenReturn(
            Optional.of(
                new SubmissionDto(
                    56L, USER, 99L, null, null, "py", "code", OffsetDateTime.now(),
                    SubmissionStatus.FAILED, 1, null)));
    assertThatThrownBy(() -> service.reportExampleDone(USER, 10L, 99L, 56L))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("尚未通过");
  }

  @Test
  void exampleDoneWithPassedSubmissionRecords() {
    when(stageRepo.findById(10L)).thenReturn(Optional.of(stage(10, 1)));
    when(submissionRepo.findSubmission(USER, 56L))
        .thenReturn(
            Optional.of(
                new SubmissionDto(
                    56L, USER, 99L, null, null, "py", "code", OffsetDateTime.now(),
                    SubmissionStatus.PASSED, 1, null)));
    when(progressRepo.addExampleDone(USER, 10L, 99L))
        .thenReturn(progress(10, 0, List.of(99L), null));
    lenient().when(progressRepo.markCompleted(USER, 10L)).thenReturn(false);

    service.reportExampleDone(USER, 10L, 99L, 56L);

    verify(progressRepo).addExampleDone(USER, 10L, 99L);
  }

  @Test
  void stageCompletionTriggersRecalcOnceIdempotently() {
    when(stageRepo.findById(10L)).thenReturn(Optional.of(stage(10, 1)));
    when(progressRepo.addExampleDone(USER, 10L, 2L))
        .thenReturn(progress(10, 240, List.of(1L, 2L), null)); // 三件套齐全
    when(progressRepo.markCompleted(USER, 10L)).thenReturn(true);

    service.reportExampleDone(USER, 10L, 2L, null);

    verify(starService, times(1)).recalc(USER, 1, StarSource.STAGE_COMPLETE);
  }

  @Test
  void alreadyCompletedStageDoesNotRecalcAgain() {
    when(stageRepo.findById(10L)).thenReturn(Optional.of(stage(10, 1)));
    when(progressRepo.addExampleDone(USER, 10L, 2L))
        .thenReturn(progress(10, 240, List.of(1L, 2L), OffsetDateTime.now()));
    when(progressRepo.markCompleted(USER, 10L)).thenReturn(false); // 已完成过

    service.reportExampleDone(USER, 10L, 2L, null);

    verify(starService, never()).recalc(anyLong(), anyInt(), any());
  }
}
