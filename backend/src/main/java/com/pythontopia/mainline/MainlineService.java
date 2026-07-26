package com.pythontopia.mainline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.error.ResourceNotFoundException;
import com.pythontopia.coursepack.repository.CoursePackRepository;
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
 *   <li><b>学</b>：本章文档全部页（{@code documentId = 185 + chapter}）按 stage 均分切片，
 *       每小节只需读完<b>自己那片</b>；{@code pages_read} 打点也只接受本片内的 pageId。
 *   <li><b>练</b>：OJ 例题（同旧 {@code required_example_count}）+ 客观题全对（{@code MainlinePracticePort}）。
 * </ul>
 *
 * <p>overview 一次取齐 stage/进度/星级/章测状态内存拼装（§11.4 避免 N+1），章级视频时长 / 页数按章缓存；
 * 实现 {@link MainlineStageCompletionPort} 供题库域（P2）答对客观题后回调重算完成。
 */
@Service
public class MainlineService implements MainlineStageCompletionPort {

  private static final Logger log = LoggerFactory.getLogger(MainlineService.class);

  /** 看：本章视频累计观看达「总时长 × 80%」即过线。 */
  private static final double VIDEO_PASS_RATIO = 0.80;

  /** 课程底座 pack id（项目唯一课程《Python语言基础》）。 */
  private static final long COURSE_PACK_ID = 43L;

  /**
   * 章 → 文档 id 的<b>回退</b>魔数映射：documentId = 185 + chapter（ch1=186 … ch7=192）。
   * 正常路径按 pack 内 {@code sort_order} 顺位动态解析（R1：重灌库 id 漂移不再取错文档）；
   * 仅当动态查询未命中（如空库启动早期）才回退此映射并告警。
   */
  private static final long COURSE_DOC_BASE = 185L;

  /** 章 → 已解析文档 id 缓存（动态命中才缓存；回退魔数不缓存，便于 seed 完成后自愈）。 */
  private final Map<Integer, Long> chapterDocumentIdCache = new java.util.concurrent.ConcurrentHashMap<>();

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
  private final CoursePackRepository coursePackRepository;
  private final MainlineOjCatalogRepository ojCatalogRepository;
  private final Optional<MainlinePracticePort> practicePort;

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
      CoursePackRepository coursePackRepository,
      MainlineOjCatalogRepository ojCatalogRepository,
      Optional<MainlinePracticePort> practicePort) {
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
    this.coursePackRepository = coursePackRepository;
    this.ojCatalogRepository = ojCatalogRepository;
    this.practicePort = practicePort;
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
    Map<Integer, List<Long>> pageIdsByChapter = new HashMap<>();

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
        List<Long> chapterPageIds =
            pageIdsByChapter.computeIfAbsent(chapter, this::chapterPageIds);
        // 章级标准 → 本小节标准（视频秒数均分、课件页切片），避免把整章要求套在每个小节上。
        MainlineStage stage =
            withChapterStandards(raw, requiredVideoSec, chapterPageIds, chapterStages.size());
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
  public StageProgressDto reportVideoProgress(long userId, long stageId, int deltaSec) {
    MainlineStage raw = requireStage(stageId);
    requireChapterUnlocked(userId, raw.chapter());
    MainlineStage stage = withChapterStandards(raw);
    int clamped = Math.min(Math.max(deltaSec, 0), properties.videoDeltaMaxSec());
    if (clamped <= 0 || !videoThrottle.tryAcquire(userId, stageId)) {
      return progressDto(userId, stage, progressRepository.find(userId, stageId).orElse(null), false);
    }
    MainlineStageProgress progress = progressRepository.addVideoSeconds(userId, stageId, clamped);
    return progressDto(userId, stage, progress, true);
  }

  /** 课件页阅读打点（单页，并集去重，幂等）。不属于本小节课件切片的 pageId 直接丢弃（no-op）。 */
  public StageProgressDto reportPageRead(long userId, long stageId, long pageId) {
    MainlineStage raw = requireStage(stageId);
    requireChapterUnlocked(userId, raw.chapter());
    MainlineStage stage = withChapterStandards(raw);
    if (!isStagePage(stage, pageId)) {
      log.debug("丢弃越界 pageId user={} stage={} page={}", userId, stageId, pageId);
      return progressDto(userId, stage, progressRepository.find(userId, stageId).orElse(null), false);
    }
    MainlineStageProgress progress = progressRepository.addPageRead(userId, stageId, pageId);
    return progressDto(userId, stage, progress, true);
  }

  /**
   * 课件页阅读批量打点（并集去重，幂等）。<b>白名单过滤</b>：只接受本小节课件切片内的 pageId，
   * 其余静默丢弃——否则把 stageDetail 回传的整章页 id 原样 POST 回来即可 0 秒过「学」。
   */
  public StageProgressDto reportPagesRead(long userId, long stageId, List<Long> pageIds) {
    MainlineStage raw = requireStage(stageId);
    requireChapterUnlocked(userId, raw.chapter());
    MainlineStage stage = withChapterStandards(raw);
    List<Long> accepted = retainStagePages(stage, pageIds);
    if (accepted.isEmpty()) {
      return progressDto(userId, stage, progressRepository.find(userId, stageId).orElse(null), false);
    }
    MainlineStageProgress progress = progressRepository.addPagesRead(userId, stageId, accepted);
    return progressDto(userId, stage, progress, true);
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
      log.info("stage 完成 user={} stage={} ch={}", userId, stage.id(), stage.chapter());
      starService.recalc(userId, stage.chapter(), StarSource.STAGE_COMPLETE);
    }
  }

  // --- 章级动态标准（看 80% / 学 整章页 / 练 客观题） ----------------------------

  /** 本章视频累计过线秒数 = round(本章内置视频总时长 × 80%)（忽略 stage 占位列）。 */
  private int requiredVideoSec(int chapter) {
    int totalSec =
        courseVideoService.listVideos(chapter).stream()
            .mapToInt(v -> durationOrZero(v.durationSec()))
            .sum();
    return (int) Math.round(totalSec * VIDEO_PASS_RATIO);
  }

  /** 本章文档全部页 id：按 pack 内 sort_order 顺位动态解析文档，未命中回退魔数映射（R1）。 */
  private List<Long> chapterPageIds(int chapter) {
    return coursePackRepository.findPageIdsByDocument(chapterDocumentId(chapter));
  }

  /**
   * 章 → 文档 id：第 N 章对应 pack 43 按 {@code sort_order, id} 排序的第 N 份文档。
   * 动态命中缓存（重启清空）；未命中（空库/seed 未完成）回退 {@code 185 + chapter} 并告警，
   * 且<b>不缓存</b>回退值——seed 完成后下次调用即自愈。
   */
  private long chapterDocumentId(int chapter) {
    Long cached = chapterDocumentIdCache.get(chapter);
    if (cached != null) {
      return cached;
    }
    Optional<Long> resolved =
        coursePackRepository.findDocumentIdByPackOrder(COURSE_PACK_ID, chapter);
    if (resolved.isPresent()) {
      chapterDocumentIdCache.put(chapter, resolved.get());
      return resolved.get();
    }
    log.warn(
        "course_document 按 pack={} 顺位 {} 未命中，回退魔数映射 documentId={}",
        COURSE_PACK_ID,
        chapter,
        COURSE_DOC_BASE + chapter);
    return COURSE_DOC_BASE + chapter;
  }

  /** 用动态章级标准覆盖 stage 的占位列（required_video_sec / courseware_page_ids / required_pages）。 */
  private MainlineStage withChapterStandards(MainlineStage stage) {
    return withChapterStandards(
        stage,
        requiredVideoSec(stage.chapter()),
        chapterPageIds(stage.chapter()),
        chapterStageCount(stage.chapter()));
  }

  /**
   * 章级标准 → <b>小节级</b>标准：{@code required_video_sec} 取章级 80% 口径按 stage 数均分；
   * {@code courseware_page_ids} 取整章页按 stage 均分后的本节切片；{@code required_pages} 置 0 以走
   * 「覆盖全部 courseware_page_ids」判定（{@link StageCompletion}）。
   *
   * <p>计数器是每小节一份的，若把章级值原样塞进每个 stage，实际要求会被放大 stage 数倍。
   */
  private MainlineStage withChapterStandards(
      MainlineStage stage, int chapterVideoSec, List<Long> chapterPageIds, int chapterStageCount) {
    int stageCount = Math.max(1, chapterStageCount);
    return new MainlineStage(
        stage.id(),
        stage.chapter(),
        stage.stageIndex(),
        stage.title(),
        stage.kcCodes(),
        stage.videoKeyword(),
        pageSlice(chapterPageIds, stage.stageIndex(), stageCount),
        stage.exampleProblemIds(),
        chapterVideoSec / stageCount,
        0,
        stage.requiredExampleCount());
  }

  /** 本章 stage 数（seed 静态数据；用于把章级标准均摊到小节）。 */
  private int chapterStageCount(int chapter) {
    return stageRepository.findByChapter(chapter).size();
  }

  /**
   * 整章课件页按 stage 均分的第 {@code stageIndex}（1 基）片：前 {@code size % stageCount} 片各多一页，
   * 保证无遗漏无重叠。stage 数 ≤1 或无页时原样返回。
   */
  static List<Long> pageSlice(List<Long> chapterPageIds, int stageIndex, int stageCount) {
    if (chapterPageIds.isEmpty() || stageCount <= 1) {
      return chapterPageIds;
    }
    int index = Math.min(Math.max(stageIndex, 1), stageCount) - 1;
    int base = chapterPageIds.size() / stageCount;
    int remainder = chapterPageIds.size() % stageCount;
    int from = index * base + Math.min(index, remainder);
    int to = from + base + (index < remainder ? 1 : 0);
    return List.copyOf(chapterPageIds.subList(from, to));
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
