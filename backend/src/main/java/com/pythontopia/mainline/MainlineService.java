package com.pythontopia.mainline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.error.ResourceNotFoundException;
import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.coursepack.service.CourseVideoService;
import com.pythontopia.mainline.domain.ChapterStar;
import com.pythontopia.mainline.domain.MainlineStage;
import com.pythontopia.mainline.domain.MainlineStageProgress;
import com.pythontopia.mainline.dto.MainlineOverviewDto;
import com.pythontopia.mainline.dto.MainlineOverviewDto.ChapterOverview;
import com.pythontopia.mainline.dto.MainlineOverviewDto.RecoveryDto;
import com.pythontopia.mainline.dto.MainlineOverviewDto.StageOverview;
import com.pythontopia.mainline.dto.StageDetailDto;
import com.pythontopia.mainline.dto.StageExampleDto;
import com.pythontopia.mainline.dto.StageProgressDto;
import com.pythontopia.mainline.dto.StageVideoDto;
import com.pythontopia.mainline.practice.MainlinePracticePort;
import com.pythontopia.mainline.practice.dto.StagePracticeQuestionDto;
import com.pythontopia.mainline.star.ChapterStarRepository;
import com.pythontopia.mainline.star.ChapterStarService;
import com.pythontopia.mainline.star.StarSource;
import com.pythontopia.mainline.test.ChapterTestRepository;
import com.pythontopia.submission.dto.SubmissionDto;
import com.pythontopia.submission.dto.SubmissionStatus;
import com.pythontopia.submission.repository.PracticeSubmissionRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

/**
 * 主线域核心服务：关卡地图总览 + stage 三件套打点（R-14 看/学/练标准聚合）。
 *
 * <p>过线口径（Phase 1 P3）：章级标准算出后<b>按本章 stage 数均摊到每个小节</b>——计数器
 * （{@code mainline_stage_progress}）本来就是每小节一份，直接把章级值套到每个小节会把要求放大 N 倍
 * （实测第 7 章：视频 5 × 6394s ≈ 8.9h 远超实际 2.2h 内容；PPT 5 × 116 = 580 页次）：
 *
 * <ul>
 *   <li><b>看</b>：章级 {@code round(本章内置视频总时长 × 0.80)} ÷ 本章 stage 数（动态算，忽略
 *       {@code mainline_stage.required_video_sec} 占位列）。
 *   <li><b>学</b>：每小节使用已校验的 KC {@code page_refs}；{@code pages_read} 打点只接受这些页。
 *   <li><b>练</b>：OJ 例题（同旧 {@code required_example_count}）+ 客观题全对（{@code MainlinePracticePort}）。
 * </ul>
 *
 * <p>overview 一次取齐 stage/进度/星级/章测状态内存拼装（§11.4 避免 N+1），章级视频时长按章缓存；
 * 实现 {@link MainlineStageCompletionPort} 供题库域（P2）答对客观题后回调重算完成。
 */
@Service
public class MainlineService implements MainlineStageCompletionPort {

  private static final Logger log = LoggerFactory.getLogger(MainlineService.class);

  /** 看：本章视频累计观看达「总时长 × 80%」即过线。 */
  private static final double VIDEO_PASS_RATIO = 0.80;

  private final MainlineStageRepository stageRepository;
  private final MainlineStageProgressRepository progressRepository;
  private final ChapterStarRepository starRepository;
  private final ChapterTestRepository chapterTestRepository;
  private final ChapterStarService starService;
  private final MainlineProperties properties;
  private final VideoProgressThrottle videoThrottle;
  private final PracticeSubmissionRepository submissionRepository;
  private final ObjectMapper objectMapper;
  private final CourseVideoService courseVideoService;
  private final MainlineOjCatalogRepository ojCatalogRepository;
  private final Optional<MainlinePracticePort> practicePort;
  private final ApplicationEventPublisher eventPublisher;

  public MainlineService(
      MainlineStageRepository stageRepository,
      MainlineStageProgressRepository progressRepository,
      ChapterStarRepository starRepository,
      ChapterTestRepository chapterTestRepository,
      ChapterStarService starService,
      MainlineProperties properties,
      VideoProgressThrottle videoThrottle,
      PracticeSubmissionRepository submissionRepository,
      ObjectMapper objectMapper,
      CourseVideoService courseVideoService,
      MainlineOjCatalogRepository ojCatalogRepository,
      Optional<MainlinePracticePort> practicePort,
      ApplicationEventPublisher eventPublisher) {
    this.stageRepository = stageRepository;
    this.progressRepository = progressRepository;
    this.starRepository = starRepository;
    this.chapterTestRepository = chapterTestRepository;
    this.starService = starService;
    this.properties = properties;
    this.videoThrottle = videoThrottle;
    this.submissionRepository = submissionRepository;
    this.objectMapper = objectMapper;
    this.courseVideoService = courseVideoService;
    this.ojCatalogRepository = ojCatalogRepository;
    this.practicePort = practicePort;
    this.eventPublisher = eventPublisher;
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

    // 章级标准按章缓存，避免按 stage 重复读视频清单 / 文档页（§11.4 avoid N+1）。
    Map<Integer, Integer> requiredVideoSecByChapter = new HashMap<>();

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
      List<MainlineStage> chapterStages = stagesByChapter.getOrDefault(chapter, List.of());
      for (MainlineStage raw : chapterStages) {
        MainlineStageProgress progress = progressByStage.get(raw.id());
        int requiredVideoSec =
            requiredVideoSecByChapter.computeIfAbsent(chapter, this::requiredVideoSec);
        // 视频秒数按小节数均分；课件页来自 stage 的 KC page_refs，不再均分整章页。
        MainlineStage stage =
            withChapterStandards(raw, requiredVideoSec, chapterStages.size());
        boolean questionsComplete = questionsComplete(userId, raw);
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
                StageCompletion.allComplete(stage, progress, questionsComplete)));
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

  /** 学习页详情：stage 静态定义 + 看/学/练真实题面 + 当前用户三件套进度（id 集合）。 */
  public StageDetailDto stageDetail(long userId, long stageId) {
    MainlineStage raw = requireStage(stageId);
    requireChapterUnlocked(userId, raw.chapter());
    MainlineStageProgress progress = progressRepository.find(userId, stageId).orElse(null);

    MainlineStage stage = withChapterStandards(raw);

    List<StageVideoDto> videos =
        courseVideoService.listVideos(raw.chapter()).stream()
            .map(v -> new StageVideoDto(v.id(), v.title(), durationOrZero(v.durationSec())))
            .toList();
    List<StageExampleDto> examples =
        ojCatalogRepository.examplesForStage(raw.chapter(), raw.stageIndex());
    List<StagePracticeQuestionDto> questions =
        practicePort
            .map(p -> p.questionsForStage(raw.chapter(), raw.stageIndex()))
            .orElse(List.of());
    int requiredQuestions =
        practicePort.map(p -> p.requiredQuestionCount(raw.chapter(), raw.stageIndex())).orElse(0);
    List<Long> questionsCorrectIds =
        practicePort.map(p -> p.correctQuestionIds(userId, stageId)).orElse(List.of());
    boolean questionsComplete = questionsComplete(userId, raw);

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
        stage.coursewarePageIds().size(),
        stage.requiredExampleCount(),
        requiredQuestions,
        videos,
        examples,
        questions,
        progress == null ? 0 : progress.videoWatchedSec(),
        progress == null ? List.of() : progress.pagesRead(),
        progress == null ? List.of() : progress.examplesDone(),
        questionsCorrectIds,
        StageCompletion.allComplete(stage, progress, questionsComplete));
  }

  // --- 三件套打点 -----------------------------------------------------------

  /** 视频时长上报：钳制单次上限 + 每分钟限频；超频 no-op（幂等软节流），不报错。 */
  @Transactional
  public StageProgressDto reportVideoProgress(long userId, long stageId, int deltaSec) {
    MainlineStage raw = requireStage(stageId);
    requireChapterUnlocked(userId, raw.chapter());
    MainlineStage stage = withChapterStandards(raw);
    int clamped = Math.min(Math.max(deltaSec, 0), properties.videoDeltaMaxSec());
    if (clamped <= 0 || !videoThrottle.tryAcquire(userId, stageId)) {
      return progressDto(userId, stage, progressRepository.find(userId, stageId).orElse(null), false);
    }
    MainlineStageProgress progress = progressRepository.addVideoSeconds(userId, stageId, clamped);
    if (stage.requiredVideoSec() > 0
        && StageCompletion.videoComplete(stage, progress)
        && progressRepository.claimVideoConsumed(userId, stageId)) {
      eventPublisher.publishEvent(
          new LearningEvent.MediaConsumed(userId, "video", stage.chapter(), stage.kcCodes()));
    }
    return progressDto(userId, stage, progress, true);
  }

  /** 课件页阅读打点（单页，并集去重，幂等）。不属于本小节课件切片的 pageId 直接丢弃（no-op）。 */
  @Transactional
  public StageProgressDto reportPageRead(long userId, long stageId, long pageId) {
    MainlineStage raw = requireStage(stageId);
    requireChapterUnlocked(userId, raw.chapter());
    MainlineStage stage = withChapterStandards(raw);
    if (!isStagePage(stage, pageId)) {
      log.debug("丢弃越界 pageId stage={} page={}", stageId, pageId);
      return progressDto(userId, stage, progressRepository.find(userId, stageId).orElse(null), false);
    }
    MainlineStageProgress progress = progressRepository.addPageRead(userId, stageId, pageId);
    publishCoursewareConsumedIfComplete(userId, stage, progress);
    return progressDto(userId, stage, progress, true);
  }

  /**
   * 课件页阅读批量打点（并集去重，幂等）。<b>白名单过滤</b>：只接受本小节 KC page_refs 中的 pageId，
   * 其他课程页静默丢弃。
   */
  @Transactional
  public StageProgressDto reportPagesRead(long userId, long stageId, List<Long> pageIds) {
    MainlineStage raw = requireStage(stageId);
    requireChapterUnlocked(userId, raw.chapter());
    MainlineStage stage = withChapterStandards(raw);
    List<Long> accepted = retainStagePages(stage, pageIds);
    if (accepted.isEmpty()) {
      return progressDto(userId, stage, progressRepository.find(userId, stageId).orElse(null), false);
    }
    MainlineStageProgress progress = progressRepository.addPagesRead(userId, stageId, accepted);
    publishCoursewareConsumedIfComplete(userId, stage, progress);
    return progressDto(userId, stage, progress, true);
  }

  private void publishCoursewareConsumedIfComplete(
      long userId, MainlineStage stage, MainlineStageProgress progress) {
    if (!stage.coursewarePageIds().isEmpty()
        && StageCompletion.pagesComplete(stage, progress)
        && progressRepository.claimCoursewareConsumed(userId, stage.id())) {
      eventPublisher.publishEvent(
          new LearningEvent.MediaConsumed(userId, "courseware", stage.chapter(), stage.kcCodes()));
    }
  }

  /**
   * 例题完成打点：题目须属于本小节（{@code example_problem_ids}，V159 已按小节回填）；带 submissionId 时
   * 强校验本人 + <b>同题</b> + PASSED（§9）；不带时按「该生该题存在任一 PASSED 提交」自动校验
   * （R2：任务完成必须提供 evidence——堵住不判题直接打点的口子）。并集去重，幂等。
   */
  public StageProgressDto reportExampleDone(
      long userId, long stageId, long problemId, Long submissionId) {
    MainlineStage raw = requireStage(stageId);
    requireChapterUnlocked(userId, raw.chapter());
    requireStageExample(raw, problemId);
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
      // 不比对题号则一次 AC 可刷满所有小节的「练」（拿 ch2 的 AC 去交 ch7 的例题）。
      if (submission.problemId() == null || submission.problemId() != problemId) {
        throw new BusinessException(
            HttpStatus.BAD_REQUEST, "EXAMPLE_SUBMISSION_PROBLEM_MISMATCH", "该提交不是这道例题的提交");
      }
      if (submission.status() != SubmissionStatus.PASSED) {
        throw new BusinessException(
            HttpStatus.BAD_REQUEST, "EXAMPLE_SUBMISSION_NOT_PASSED", "该提交尚未通过判题");
      }
    } else if (submissionRepository.list(userId, problemId, SubmissionStatus.PASSED, 1, 0)
        .isEmpty()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "EXAMPLE_EVIDENCE_REQUIRED",
          "该例题还没有通过判题的提交，先去训练场提交通过后再标记完成");
    }
    MainlineStageProgress progress = progressRepository.addExampleDone(userId, stageId, problemId);
    return progressDto(userId, withChapterStandards(raw), progress, true);
  }

  // --- 完成重算端口（MainlineStageCompletionPort，供 P2 答对客观题后回调） ----------

  /** 重新判定该用户该 stage 是否三件套齐全；首次齐全则置完成并重算星级（幂等）。 */
  @Override
  public void recheckCompletion(long userId, long stageId) {
    MainlineStage raw = stageRepository.findById(stageId).orElse(null);
    if (raw == null) {
      return; // 防御：stage 不存在则跳过（不抛错，避免打断 P2 的答题打点）。
    }
    MainlineStageProgress progress = progressRepository.find(userId, stageId).orElse(null);
    maybeComplete(userId, withChapterStandards(raw), progress, questionsComplete(userId, raw));
  }

  // --- 内部 -----------------------------------------------------------------

  /**
   * 统一回包：{@code stage} 须为已套用小节级标准的实例（{@link #withChapterStandards}）；
   * {@code applyCompletion} 为 true 时（即发生过有效写入）先触发完成判定。
   */
  private StageProgressDto progressDto(
      long userId, MainlineStage stage, MainlineStageProgress progress, boolean applyCompletion) {
    boolean questionsComplete = questionsComplete(userId, stage);
    if (applyCompletion) {
      maybeComplete(userId, stage, progress, questionsComplete);
    }
    return toDto(stage, progress, questionsComplete);
  }

  /** 三件套齐全则原子置完成并触发一次星级重算（markCompleted 仅在首次完成返回 true，幂等）。 */
  private void maybeComplete(
      long userId, MainlineStage stage, MainlineStageProgress progress, boolean questionsComplete) {
    if (StageCompletion.allComplete(stage, progress, questionsComplete)
        && progressRepository.markCompleted(userId, stage.id())) {
      log.info("stage 完成 stage={} ch={}", stage.id(), stage.chapter());
      starService.recalc(userId, stage.chapter(), StarSource.STAGE_COMPLETE);
    }
  }

  // --- 章级视频标准与小节 KC 课件页 --------------------------------------------

  /** 本章视频累计过线秒数 = round(本章内置视频总时长 × 80%)（忽略 stage 占位列）。 */
  private int requiredVideoSec(int chapter) {
    int totalSec =
        courseVideoService.listVideos(chapter).stream()
            .mapToInt(v -> durationOrZero(v.durationSec()))
            .sum();
    return (int) Math.round(totalSec * VIDEO_PASS_RATIO);
  }

  /** 用动态章级视频标准覆盖 stage 的占位值，保留迁移校验过的 KC 课件页。 */
  private MainlineStage withChapterStandards(MainlineStage stage) {
    return withChapterStandards(
        stage,
        requiredVideoSec(stage.chapter()),
        chapterStageCount(stage.chapter()));
  }

  /**
   * 章级视频 80% 要求按 stage 数均分；课件页来自课程包中该小节 KC 的 page_refs。
   *
   * <p>课程页数组由迁移依据 {@code course_kc.page_refs} 回填，空数组时 {@link StageCompletion} 会 fail closed。
   */
  private MainlineStage withChapterStandards(
      MainlineStage stage, int chapterVideoSec, int chapterStageCount) {
    int stageCount = Math.max(1, chapterStageCount);
    return new MainlineStage(
        stage.id(),
        stage.chapter(),
        stage.stageIndex(),
        stage.title(),
        stage.kcCodes(),
        stage.videoKeyword(),
        stage.coursewarePageIds(),
        stage.exampleProblemIds(),
        chapterVideoSec / stageCount,
        0,
        stage.requiredExampleCount());
  }

  /** 本章 stage 数（seed 静态数据；用于把章级标准均摊到小节）。 */
  private int chapterStageCount(int chapter) {
    return stageRepository.findByChapter(chapter).size();
  }

  /** 该 pageId 是否属于本小节课件切片（切片为空=占位期，一律视为不可打点）。 */
  private static boolean isStagePage(MainlineStage stage, long pageId) {
    return stage.coursewarePageIds().contains(pageId);
  }

  /** 批量打点白名单过滤：只留本小节切片内的 pageId（去重，保持入参顺序）。 */
  private static List<Long> retainStagePages(MainlineStage stage, List<Long> pageIds) {
    if (pageIds == null || pageIds.isEmpty() || stage.coursewarePageIds().isEmpty()) {
      return List.of();
    }
    java.util.Set<Long> allowed = new java.util.HashSet<>(stage.coursewarePageIds());
    return pageIds.stream().filter(allowed::contains).distinct().toList();
  }

  /** 例题须属于本小节（{@code example_problem_ids} 为空=未回填，放行以兼容占位期）。 */
  private static void requireStageExample(MainlineStage stage, long problemId) {
    List<Long> allowed = stage.exampleProblemIds();
    if (!allowed.isEmpty() && !allowed.contains(problemId)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "EXAMPLE_NOT_IN_STAGE", "该例题不属于本小节");
    }
  }

  /** 章解锁硬 gate：前一章不足 1★ 时所有写路径与详情页一律拒绝（overview 的 unlocked 只是展示位）。 */
  private void requireChapterUnlocked(long userId, int chapter) {
    ChapterUnlockGuard.requireUnlocked(starRepository, userId, chapter);
  }

  /** 客观题是否全对（题库 bean 缺失 / 无题要求时降级为 true）。 */
  private boolean questionsComplete(long userId, MainlineStage stage) {
    return practicePort
        .map(p -> p.questionsComplete(userId, stage.id(), stage.chapter(), stage.stageIndex()))
        .orElse(true);
  }

  private static int durationOrZero(Integer durationSec) {
    return durationSec == null ? 0 : durationSec;
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

  /** {@code stage} 须为已套用章级动态标准的实例（{@link #withChapterStandards}）。 */
  private StageProgressDto toDto(
      MainlineStage stage, MainlineStageProgress progress, boolean questionsComplete) {
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
        StageCompletion.allComplete(stage, progress, questionsComplete));
  }

  private MainlineStage requireStage(long stageId) {
    return stageRepository
        .findById(stageId)
        .orElseThrow(() -> new ResourceNotFoundException("stage 不存在: " + stageId));
  }
}
