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
import com.pythontopia.coursepack.dto.CourseVideoDto;
import com.pythontopia.coursepack.service.CourseVideoService;
import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.mainline.domain.ChapterStar;
import com.pythontopia.mainline.domain.MainlineStage;
import com.pythontopia.mainline.domain.MainlineStageProgress;
import com.pythontopia.mainline.dto.MainlineOverviewDto;
import com.pythontopia.mainline.dto.StageDetailDto;
import com.pythontopia.mainline.dto.StageExampleDto;
import com.pythontopia.mainline.dto.StageProgressDto;
import com.pythontopia.mainline.practice.MainlinePracticePort;
import com.pythontopia.mainline.practice.PracticeQuestionType;
import com.pythontopia.mainline.practice.dto.StagePracticeQuestionDto;
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
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
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
  private final CourseVideoService courseVideoService = mock(CourseVideoService.class);
  private final MainlineOjCatalogRepository ojCatalogRepository =
      mock(MainlineOjCatalogRepository.class);
  private final MainlinePracticePort practicePort = mock(MainlinePracticePort.class);
  private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
  private final MainlineProperties properties =
      new MainlineProperties(null, null, null, null); // 默认：deltaMax=120, writesPerMin=2

  // 默认：题库 bean 缺失（Optional.empty）→ 客观题维度降级为「已完成」。
  private final MainlineService service =
      new MainlineService(
          stageRepo, progressRepo, starRepo, testRepo, starService, properties, throttle,
          submissionRepo, mapper, courseVideoService, ojCatalogRepository,
          Optional.empty(), eventPublisher);

  // 题库 bean 就位（Optional.of）→ 过线计入「客观题是否全对」。
  private final MainlineService serviceWithPractice =
      new MainlineService(
          stageRepo, progressRepo, starRepo, testRepo, starService, properties, throttle,
          submissionRepo, mapper, courseVideoService, ojCatalogRepository,
          Optional.of(practicePort), eventPublisher);

  private MainlineStage stage(long id, int chapter) {
    return new MainlineStage(
        id, chapter, 1, "s" + id, List.of("PY43-KC-001"), "kw", List.of(), List.of(), 240, 0, 2);
  }

  private MainlineStage stage(long id, int chapter, int stageIndex, List<Long> exampleProblemIds) {
    return new MainlineStage(
        id, chapter, stageIndex, "s" + id, List.of("PY43-KC-001"), "kw", List.of(),
        exampleProblemIds, 240, 0, 2);
  }

  private MainlineStage stageWithPages(
      long id, int chapter, int stageIndex, List<Long> pageIds, List<Long> exampleProblemIds) {
    return new MainlineStage(
        id, chapter, stageIndex, "s" + id, List.of("PY43-KC-001"), "kw", pageIds,
        exampleProblemIds, 240, 0, 2);
  }

  /** 前一章已达 1★ → 章解锁 gate 放行（锁章路径另有专测）。 */
  private void stubChapterUnlocked(int chapter) {
    when(starRepo.find(USER, chapter - 1))
        .thenReturn(
            Optional.of(
                new ChapterStar(1L, USER, chapter - 1, 1, 70, mapper.createObjectNode(), null)));
  }

  private MainlineStageProgress progress(
      long stageId, int videoSec, List<Long> examples, OffsetDateTime completedAt) {
    return new MainlineStageProgress(
        stageId, USER, stageId, videoSec, List.of(), examples, List.of(), completedAt,
        OffsetDateTime.now());
  }

  private MainlineStageProgress progressWithPages(long stageId, List<Long> pages) {
    return new MainlineStageProgress(
        stageId, USER, stageId, 0, pages, List.of(), List.of(), null, OffsetDateTime.now());
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
    assertThat(ch1.stages().get(0).completed()).isFalse(); // mock 中没有课程页数据时 fail closed

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
  void videoConsumptionEmitsOnceOnlyAfterTheRequiredWatchTime() {
    when(stageRepo.findById(10L)).thenReturn(Optional.of(stage(10, 1)));
    when(courseVideoService.listVideos(1))
        .thenReturn(List.of(new CourseVideoDto(100L, 1, "v1", 1000, List.of(), true)));
    when(throttle.tryAcquire(USER, 10L)).thenReturn(true);
    when(progressRepo.addVideoSeconds(USER, 10L, 120))
        .thenReturn(progress(10, 800, List.of(), null));
    when(progressRepo.claimVideoConsumed(USER, 10L)).thenReturn(true, false);

    service.reportVideoProgress(USER, 10L, 120);
    service.reportVideoProgress(USER, 10L, 120);

    ArgumentCaptor<LearningEvent.MediaConsumed> event =
        ArgumentCaptor.forClass(LearningEvent.MediaConsumed.class);
    verify(eventPublisher).publishEvent(event.capture());
    assertThat(event.getValue().mediaType()).isEqualTo("video");
    assertThat(event.getValue().chapter()).isEqualTo(1);
    assertThat(event.getValue().kcCodes()).containsExactly("PY43-KC-001");
  }

  @Test
  void coursewareConsumptionUsesCompletedServerPageProgress() {
    when(stageRepo.findById(10L))
        .thenReturn(Optional.of(stageWithPages(10, 1, 1, List.of(5001L, 5002L), List.of())));
    when(progressRepo.addPagesRead(USER, 10L, List.of(5001L, 5002L)))
        .thenReturn(progressWithPages(10L, List.of(5001L, 5002L)));
    when(progressRepo.claimCoursewareConsumed(USER, 10L)).thenReturn(true);

    service.reportPagesRead(USER, 10L, List.of(5001L, 5002L));

    ArgumentCaptor<LearningEvent.MediaConsumed> event =
        ArgumentCaptor.forClass(LearningEvent.MediaConsumed.class);
    verify(eventPublisher).publishEvent(event.capture());
    assertThat(event.getValue().mediaType()).isEqualTo("courseware");
    assertThat(event.getValue().kcCodes()).containsExactly("PY43-KC-001");
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

  /** R2：无 submissionId 的打点要求「该生该题已有 PASSED 提交」自动 evidence。 */
  private void stubPassedSubmission(long problemId) {
    when(submissionRepo.list(USER, problemId, SubmissionStatus.PASSED, 1, 0))
        .thenReturn(
            List.of(
                new SubmissionDto(
                    77L, USER, problemId, null, null, "py", "code", OffsetDateTime.now(),
                    SubmissionStatus.PASSED, 1, null)));
  }

  @Test
  void exampleDoneWithoutSubmissionIdRequiresPassedEvidence() {
    when(stageRepo.findById(10L)).thenReturn(Optional.of(stage(10, 1)));
    when(submissionRepo.list(USER, 2L, SubmissionStatus.PASSED, 1, 0)).thenReturn(List.of());

    assertThatThrownBy(() -> service.reportExampleDone(USER, 10L, 2L, null))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("先去训练场");
  }

  @Test
  void stageCompletionTriggersRecalcOnceIdempotently() {
    when(stageRepo.findById(10L))
        .thenReturn(Optional.of(stageWithPages(10, 1, 1, List.of(8001L), List.of())));
    stubPassedSubmission(2L);
    when(progressRepo.addExampleDone(USER, 10L, 2L))
        .thenReturn(
            new MainlineStageProgress(
                10L, USER, 10L, 240, List.of(8001L), List.of(1L, 2L), List.of(), null,
                OffsetDateTime.now())); // 三件套齐全且确实读过课程页
    when(progressRepo.markCompleted(USER, 10L)).thenReturn(true);

    service.reportExampleDone(USER, 10L, 2L, null);

    verify(starService, times(1)).recalc(USER, 1, StarSource.STAGE_COMPLETE);
  }

  @Test
  void alreadyCompletedStageDoesNotRecalcAgain() {
    when(stageRepo.findById(10L)).thenReturn(Optional.of(stage(10, 1)));
    stubPassedSubmission(2L);
    when(progressRepo.addExampleDone(USER, 10L, 2L))
        .thenReturn(progress(10, 240, List.of(1L, 2L), OffsetDateTime.now()));
    when(progressRepo.markCompleted(USER, 10L)).thenReturn(false); // 已完成过

    service.reportExampleDone(USER, 10L, 2L, null);

    verify(starService, never()).recalc(anyLong(), anyInt(), any());
  }

  @Test
  void stageDetailPreservesKcReferencedCoursewarePages() {
    when(stageRepo.findById(10L))
        .thenReturn(Optional.of(stageWithPages(10, 1, 1, List.of(8001L, 8002L), List.of())));
    when(progressRepo.find(USER, 10L)).thenReturn(Optional.empty());
    when(courseVideoService.listVideos(1)).thenReturn(List.of());
    when(ojCatalogRepository.examplesForStage(1, 1)).thenReturn(List.of());
    StageDetailDto dto = service.stageDetail(USER, 10L);

    assertThat(dto.coursewarePageIds()).containsExactly(8001L, 8002L);
  }

  @Test
  void stageDetailFillsChapterStandardsVideosPagesExamplesQuestions() {
    when(stageRepo.findById(10L))
        .thenReturn(Optional.of(stageWithPages(10, 1, 1,
            List.of(5001L, 5002L, 5003L), List.of())));
    when(progressRepo.find(USER, 10L))
        .thenReturn(Optional.of(progress(10, 1000, List.of(9001L), null)));
    // 看：本章两段视频 600 + 400 = 1000s → requiredVideoSec = round(1000 × 0.8) = 800。
    when(courseVideoService.listVideos(1))
        .thenReturn(
            List.of(
                new CourseVideoDto(100L, 1, "v1", 600, List.of(), true),
                new CourseVideoDto(101L, 1, "v2", 400, List.of(), true)));
    // 学：stage 读取该小节 KC 对应的课件页。
    // 练-OJ：catalog 展示层。
    when(ojCatalogRepository.examplesForStage(1, 1))
        .thenReturn(List.of(new StageExampleDto(9001L, "OJ-01", "两数之和", "PY43-KC-001")));
    // 练-客观题：题面 + 需答对数 + 已答对 + 未全对。
    when(practicePort.questionsForStage(1, 1))
        .thenReturn(
            List.of(
                new StagePracticeQuestionDto(
                    7001L, "PY43-KC-001", PracticeQuestionType.CHOICE, 1, "stem", List.of("A", "B"),
                    0)));
    when(practicePort.requiredQuestionCount(1, 1)).thenReturn(4);
    when(practicePort.correctQuestionIds(USER, 10L)).thenReturn(List.of(7001L));
    when(practicePort.questionsComplete(USER, 10L, 1, 1)).thenReturn(false);

    StageDetailDto dto = serviceWithPractice.stageDetail(USER, 10L);

    assertThat(dto.requiredVideoSec()).isEqualTo(800);
    assertThat(dto.coursewarePageIds()).containsExactly(5001L, 5002L, 5003L);
    assertThat(dto.requiredPages()).isEqualTo(3);
    assertThat(dto.videos()).hasSize(2);
    assertThat(dto.videos().get(0).durationSec()).isEqualTo(600);
    assertThat(dto.examples()).hasSize(1);
    assertThat(dto.examples().get(0).displayNo()).isEqualTo("OJ-01");
    assertThat(dto.questions()).hasSize(1);
    assertThat(dto.requiredQuestions()).isEqualTo(4);
    assertThat(dto.questionsCorrectIds()).containsExactly(7001L);
    assertThat(dto.completed()).isFalse(); // 客观题未全对 → 未过线。
  }

  @Test
  void questionsIncompleteBlocksStageCompletionWhenPracticePresent() {
    when(stageRepo.findById(10L)).thenReturn(Optional.of(stage(10, 1)));
    stubPassedSubmission(2L);
    // 看（无视频→requiredVideoSec=0）/ 学（无整章页→默认过线）/ 练-OJ（例题 [1,2]≥2）均达标。
    when(progressRepo.addExampleDone(USER, 10L, 2L))
        .thenReturn(progress(10, 0, List.of(1L, 2L), null));
    when(practicePort.questionsComplete(USER, 10L, 1, 1)).thenReturn(false); // 客观题未全对

    serviceWithPractice.reportExampleDone(USER, 10L, 2L, null);

    verify(progressRepo, never()).markCompleted(anyLong(), anyLong());
    verify(starService, never()).recalc(anyLong(), anyInt(), any());
  }

  @Test
  void reportPagesReadBatchAppendsAndCompletesViaPort() {
    when(stageRepo.findById(10L))
        .thenReturn(Optional.of(stageWithPages(10, 1, 1,
            List.of(5001L, 5002L, 5003L), List.of())));
    // 学：本小节 KC 的三张课件页读齐后过线。
    MainlineStageProgress full =
        new MainlineStageProgress(
            10L, USER, 10L, 0, List.of(5001L, 5002L, 5003L), List.of(1L, 2L), List.of(), null,
            OffsetDateTime.now());
    when(progressRepo.addPagesRead(USER, 10L, List.of(5001L, 5002L, 5003L))).thenReturn(full);
    when(progressRepo.claimCoursewareConsumed(USER, 10L)).thenReturn(true);
    when(progressRepo.markCompleted(USER, 10L)).thenReturn(true);

    StageProgressDto dto = service.reportPagesRead(USER, 10L, List.of(5001L, 5002L, 5003L));

    assertThat(dto.pagesComplete()).isTrue();
    assertThat(dto.completed()).isTrue();
    verify(progressRepo).addPagesRead(USER, 10L, List.of(5001L, 5002L, 5003L));
    verify(starService, times(1)).recalc(USER, 1, StarSource.STAGE_COMPLETE);
  }

  // --- 例题打点：题号 / 归属校验 -------------------------------------------

  /** 带 submissionId 时必须比对题号：否则一次 AC 可刷满所有小节的「练」。 */
  @Test
  void exampleDoneRejectsSubmissionOfAnotherProblem() {
    when(stageRepo.findById(10L)).thenReturn(Optional.of(stage(10, 1)));
    when(submissionRepo.findSubmission(USER, 56L))
        .thenReturn(
            Optional.of(
                new SubmissionDto(
                    56L, USER, 99L, null, null, "py", "code", OffsetDateTime.now(),
                    SubmissionStatus.PASSED, 1, null)));

    assertThatThrownBy(() -> service.reportExampleDone(USER, 10L, 42L, 56L))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("不是这道例题的提交");

    verify(progressRepo, never()).addExampleDone(anyLong(), anyLong(), anyLong());
  }

  /** 题号必须落在本小节 example_problem_ids 内（V159 已按小节回填），挡住写入不存在题号。 */
  @Test
  void exampleDoneRejectsProblemOutsideStage() {
    when(stageRepo.findById(10L)).thenReturn(Optional.of(stage(10, 1, 1, List.of(476L))));

    assertThatThrownBy(() -> service.reportExampleDone(USER, 10L, 99999L, null))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("不属于本小节");

    verify(submissionRepo, never()).list(anyLong(), anyLong(), any(), anyInt(), anyInt());
    verify(progressRepo, never()).addExampleDone(anyLong(), anyLong(), anyLong());
  }

  // --- 章级标准均摊到小节 ---------------------------------------------------

  /** 章级视频秒数按 stage 数均分、整章课件页按 stage 切片，不再把整章要求套在每个小节上。 */
  @Test
  void chapterStandardsAreSplitAcrossStagesOfTheChapter() {
    stubChapterUnlocked(7);
    MainlineStage second = new MainlineStage(
        70L, 7, 2, "s70", List.of("PY43-KC-006", "PY43-KC-007"), "kw",
        List.of(9001L, 9002L), List.of(), 240, 0, 2);
    when(stageRepo.findById(70L)).thenReturn(Optional.of(second));
    when(stageRepo.findByChapter(7))
        .thenReturn(
            List.of(
                stage(69L, 7, 1, List.of()), second, stage(71L, 7, 3, List.of()),
                stage(72L, 7, 4, List.of()), stage(73L, 7, 5, List.of())));
    when(progressRepo.find(USER, 70L)).thenReturn(Optional.empty());
    // 看：本章视频共 1000s → 章级 800s → 5 个小节各 160s。
    when(courseVideoService.listVideos(7))
        .thenReturn(List.of(new CourseVideoDto(700L, 7, "v", 1000, List.of(), true)));
    // 学：使用本小节 KC 在课程种子中的权威页引用，不按整章页数均分。
    when(ojCatalogRepository.examplesForStage(7, 2)).thenReturn(List.of());

    StageDetailDto dto = service.stageDetail(USER, 70L);

    assertThat(dto.requiredVideoSec()).isEqualTo(160);
    assertThat(dto.coursewarePageIds()).containsExactly(9001L, 9002L);
    assertThat(dto.requiredPages()).isEqualTo(2);
  }

  /** 「学」打点白名单：只接受本小节 KC 引用的 pageId，其他课程页无效。 */
  @Test
  void pagesReadKeepsOnlyPagesReferencedByStageKcs() {
    stubChapterUnlocked(7);
    MainlineStage second = stageWithPages(70L, 7, 2, List.of(3L, 4L), List.of());
    when(stageRepo.findById(70L)).thenReturn(Optional.of(second));
    when(stageRepo.findByChapter(7))
        .thenReturn(
            List.of(
                stage(69L, 7, 1, List.of()), second, stage(71L, 7, 3, List.of()),
                stage(72L, 7, 4, List.of()), stage(73L, 7, 5, List.of())));
    when(courseVideoService.listVideos(7)).thenReturn(List.of());
    when(progressRepo.addPagesRead(USER, 70L, List.of(3L, 4L)))
        .thenReturn(progress(70, 0, List.of(), null));

    service.reportPagesRead(USER, 70L, List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L));

    verify(progressRepo).addPagesRead(USER, 70L, List.of(3L, 4L));
    verify(progressRepo, never()).addPagesRead(USER, 70L, List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L));
  }

  // --- 章解锁 gate ---------------------------------------------------------

  /** 前一章 0★ 时详情页即拒绝，堵住「直接跳到第 7 章」。 */
  @Test
  void stageDetailRejectedWhenPreviousChapterIsLocked() {
    when(stageRepo.findById(70L)).thenReturn(Optional.of(stage(70L, 7, 1, List.of())));
    when(starRepo.find(USER, 6)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.stageDetail(USER, 70L))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("尚未解锁");

  }

  /** 打点写路径同样 gate（不只是读路径）。 */
  @Test
  void videoProgressRejectedWhenPreviousChapterIsLocked() {
    when(stageRepo.findById(70L)).thenReturn(Optional.of(stage(70L, 7, 1, List.of())));
    when(starRepo.find(USER, 6))
        .thenReturn(
            Optional.of(new ChapterStar(1L, USER, 6, 0, null, mapper.createObjectNode(), null)));

    assertThatThrownBy(() -> service.reportVideoProgress(USER, 70L, 60))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("尚未解锁");

    verify(progressRepo, never()).addVideoSeconds(anyLong(), anyLong(), anyInt());
  }

}
