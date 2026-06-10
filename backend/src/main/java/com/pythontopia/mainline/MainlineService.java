package com.pythontopia.mainline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.error.ResourceNotFoundException;
import com.pythontopia.mainline.domain.ChapterStar;
import com.pythontopia.mainline.domain.MainlineStage;
import com.pythontopia.mainline.domain.MainlineStageProgress;
import com.pythontopia.mainline.dto.MainlineOverviewDto;
import com.pythontopia.mainline.dto.MainlineOverviewDto.ChapterOverview;
import com.pythontopia.mainline.dto.MainlineOverviewDto.RecoveryDto;
import com.pythontopia.mainline.dto.MainlineOverviewDto.StageOverview;
import com.pythontopia.mainline.dto.StageDetailDto;
import com.pythontopia.mainline.dto.StageProgressDto;
import com.pythontopia.mainline.star.ChapterStarRepository;
import com.pythontopia.mainline.star.ChapterStarService;
import com.pythontopia.mainline.star.StarSource;
import com.pythontopia.mainline.test.ChapterTestRepository;
import com.pythontopia.submission.dto.SubmissionDto;
import com.pythontopia.submission.dto.SubmissionStatus;
import com.pythontopia.submission.repository.PracticeSubmissionRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 主线域核心服务：关卡地图总览 + stage 三件套打点。
 *
 * <p>总览（{@link #overview}）按 user <b>一次取齐</b> stage / 进度 / 星级 / 章测状态 4 类数据后内存拼装，
 * 不在章/ stage 维度发起循环查询（§11.4 avoid N+1）。
 *
 * <p>打点全部<b>幂等</b>：视频累加（钳制 + 限频）、课件页/例题并集去重；三件套齐全则原子置完成并触发一次星级重算。
 */
@Service
public class MainlineService {

  private static final Logger log = LoggerFactory.getLogger(MainlineService.class);

  private final MainlineStageRepository stageRepository;
  private final MainlineStageProgressRepository progressRepository;
  private final ChapterStarRepository starRepository;
  private final ChapterTestRepository chapterTestRepository;
  private final ChapterStarService starService;
  private final MainlineProperties properties;
  private final VideoProgressThrottle videoThrottle;
  private final PracticeSubmissionRepository submissionRepository;
  private final ObjectMapper objectMapper;

  public MainlineService(
      MainlineStageRepository stageRepository,
      MainlineStageProgressRepository progressRepository,
      ChapterStarRepository starRepository,
      ChapterTestRepository chapterTestRepository,
      ChapterStarService starService,
      MainlineProperties properties,
      VideoProgressThrottle videoThrottle,
      PracticeSubmissionRepository submissionRepository,
      ObjectMapper objectMapper) {
    this.stageRepository = stageRepository;
    this.progressRepository = progressRepository;
    this.starRepository = starRepository;
    this.chapterTestRepository = chapterTestRepository;
    this.starService = starService;
    this.properties = properties;
    this.videoThrottle = videoThrottle;
    this.submissionRepository = submissionRepository;
    this.objectMapper = objectMapper;
  }

  // --- 总览 -----------------------------------------------------------------

  public MainlineOverviewDto overview(long userId) {
    List<MainlineStage> stages = stageRepository.findAll();
    Map<Long, MainlineStageProgress> progressByStage =
        progressRepository.findByUser(userId).stream()
            .collect(Collectors.toMap(MainlineStageProgress::stageId, p -> p, (a, b) -> b));
    Map<Integer, ChapterStar> starByChapter =
        starRepository.findByUser(userId).stream()
            .collect(Collectors.toMap(ChapterStar::chapter, s -> s, (a, b) -> b));
    Map<Integer, String> testStatusByChapter =
        chapterTestRepository.findLatestStatusByChapter(userId);
    Map<Integer, List<MainlineStage>> stagesByChapter =
        stages.stream()
            .collect(
                Collectors.groupingBy(
                    MainlineStage::chapter, LinkedHashMap::new, Collectors.toList()));

    List<ChapterOverview> chapters = new ArrayList<>();
    int totalStars = 0;
    int prevStars = 1; // 第 1 章无前置门槛（视作前置已满星）。
    for (int chapter = 1; chapter <= ChapterCatalog.CHAPTER_COUNT; chapter++) {
      ChapterStar star = starByChapter.get(chapter);
      int stars = star == null ? 0 : star.stars();
      Integer bestScore = star == null ? null : star.bestTestScore();
      JsonNode breakdown =
          star == null || star.breakdown() == null
              ? objectMapper.createObjectNode()
              : star.breakdown();
      boolean unlocked = chapter == 1 || prevStars >= 1;

      List<StageOverview> stageViews = new ArrayList<>();
      for (MainlineStage stage : stagesByChapter.getOrDefault(chapter, List.of())) {
        MainlineStageProgress progress = progressByStage.get(stage.id());
        stageViews.add(
            new StageOverview(
                stage.id(),
                stage.stageIndex(),
                stage.title(),
                progress == null ? 0 : progress.videoWatchedSec(),
                stage.requiredVideoSec(),
                StageCompletion.distinctPages(progress),
                StageCompletion.requiredPagesDisplay(stage),
                StageCompletion.distinctExamples(progress),
                stage.requiredExampleCount(),
                StageCompletion.allComplete(stage, progress)));
      }

      chapters.add(
          new ChapterOverview(
              chapter,
              ChapterCatalog.title(chapter),
              unlocked,
              stars,
              bestScore,
              stageViews,
              testStatusByChapter.getOrDefault(chapter, "NONE"),
              breakdown,
              recovery(breakdown)));
      totalStars += stars;
      prevStars = stars;
    }
    return new MainlineOverviewDto(totalStars, chapters);
  }

  /** 学习页详情：stage 静态定义 + 当前用户三件套进度（id 集合）。overview 保持轻量，本端按需取。 */
  public StageDetailDto stageDetail(long userId, long stageId) {
    MainlineStage stage = requireStage(stageId);
    MainlineStageProgress progress = progressRepository.find(userId, stageId).orElse(null);
    return new StageDetailDto(
        stage.id(),
        stage.chapter(),
        ChapterCatalog.title(stage.chapter()),
        stage.stageIndex(),
        stage.title(),
        stage.kcCodes(),
        stage.videoKeyword(),
        stage.coursewarePageIds(),
        stage.exampleProblemIds(),
        stage.requiredVideoSec(),
        stage.requiredPages(),
        stage.requiredExampleCount(),
        progress == null ? 0 : progress.videoWatchedSec(),
        progress == null ? List.of() : progress.pagesRead(),
        progress == null ? List.of() : progress.examplesDone(),
        StageCompletion.allComplete(stage, progress));
  }

  // --- 三件套打点 -----------------------------------------------------------

  /** 视频时长上报：钳制单次上限 + 每分钟限频；超频 no-op（幂等软节流），不报错。 */
  public StageProgressDto reportVideoProgress(long userId, long stageId, int deltaSec) {
    MainlineStage stage = requireStage(stageId);
    int clamped = Math.min(Math.max(deltaSec, 0), properties.videoDeltaMaxSec());
    if (clamped <= 0 || !videoThrottle.tryAcquire(userId, stageId)) {
      return toDto(stage, progressRepository.find(userId, stageId).orElse(null));
    }
    MainlineStageProgress progress =
        progressRepository.addVideoSeconds(userId, stageId, clamped);
    maybeComplete(userId, stage, progress);
    return toDto(stage, progress);
  }

  /** 课件页阅读打点（并集去重，幂等）。 */
  public StageProgressDto reportPageRead(long userId, long stageId, long pageId) {
    MainlineStage stage = requireStage(stageId);
    MainlineStageProgress progress = progressRepository.addPageRead(userId, stageId, pageId);
    maybeComplete(userId, stage, progress);
    return toDto(stage, progress);
  }

  /** 例题完成打点：带 submissionId 时强校验本人且 PASSED（§9）；并集去重，幂等。 */
  public StageProgressDto reportExampleDone(
      long userId, long stageId, long problemId, Long submissionId) {
    MainlineStage stage = requireStage(stageId);
    if (submissionId != null) {
      SubmissionDto submission =
          submissionRepository
              .findSubmission(userId, submissionId)
              .orElseThrow(
                  () ->
                      new BusinessException(
                          HttpStatus.BAD_REQUEST,
                          "EXAMPLE_SUBMISSION_INVALID",
                          "提交不存在或不属于本人"));
      if (submission.status() != SubmissionStatus.PASSED) {
        throw new BusinessException(
            HttpStatus.BAD_REQUEST, "EXAMPLE_SUBMISSION_NOT_PASSED", "该提交尚未通过判题");
      }
    }
    MainlineStageProgress progress = progressRepository.addExampleDone(userId, stageId, problemId);
    maybeComplete(userId, stage, progress);
    return toDto(stage, progress);
  }

  // --- 内部 -----------------------------------------------------------------

  /** 三件套齐全则原子置完成并触发一次星级重算（markCompleted 仅在首次完成返回 true，幂等）。 */
  private void maybeComplete(long userId, MainlineStage stage, MainlineStageProgress progress) {
    if (StageCompletion.allComplete(stage, progress)
        && progressRepository.markCompleted(userId, stage.id())) {
      log.info("stage 完成 user={} stage={} ch={}", userId, stage.id(), stage.chapter());
      starService.recalc(userId, stage.chapter(), StarSource.STAGE_COMPLETE);
    }
  }

  private RecoveryDto recovery(JsonNode breakdown) {
    JsonNode recovered = breakdown == null ? null : breakdown.get("recovered");
    boolean errorbook = false;
    boolean endless = false;
    if (recovered != null && recovered.isArray()) {
      for (JsonNode node : recovered) {
        String value = node.asText("");
        if ("ERRORBOOK_RECOVERY".equals(value)) {
          errorbook = true;
        } else if ("ENDLESS_RECOVERY".equals(value)) {
          endless = true;
        }
      }
    }
    return new RecoveryDto(errorbook, endless);
  }

  private StageProgressDto toDto(MainlineStage stage, MainlineStageProgress progress) {
    return new StageProgressDto(
        stage.id(),
        stage.chapter(),
        stage.stageIndex(),
        progress == null ? 0 : progress.videoWatchedSec(),
        stage.requiredVideoSec(),
        StageCompletion.videoComplete(stage, progress),
        StageCompletion.distinctPages(progress),
        StageCompletion.requiredPagesDisplay(stage),
        StageCompletion.pagesComplete(stage, progress),
        StageCompletion.distinctExamples(progress),
        stage.requiredExampleCount(),
        StageCompletion.examplesComplete(stage, progress),
        StageCompletion.allComplete(stage, progress));
  }

  private MainlineStage requireStage(long stageId) {
    return stageRepository
        .findById(stageId)
        .orElseThrow(() -> new ResourceNotFoundException("stage 不存在: " + stageId));
  }
}
