package com.pythontopia.dialog;

import com.pythontopia.auth.domain.UserAccount;
import com.pythontopia.auth.repository.UserRepository;
import com.pythontopia.dialog.DialogContextRepository.EnergyRow;
import com.pythontopia.dialog.DialogContextRepository.KcChapterRow;
import com.pythontopia.dialog.DialogContextRepository.KcFailureRow;
import com.pythontopia.dialog.DialogContextRepository.KcMasteryRow;
import com.pythontopia.dialog.DialogContextRepository.LastFailureRow;
import com.pythontopia.dialog.DialogContextRepository.PeerRow;
import com.pythontopia.dialog.DialogContextRepository.ProblemRow;
import com.pythontopia.dialog.DialogContextRepository.VerdictCountRow;
import com.pythontopia.learner.profile.dto.CognitiveStyleDto;
import com.pythontopia.learner.profile.dto.LearnerProfileDto;
import com.pythontopia.learner.profile.repository.ProfileRepository;
import com.pythontopia.world.calendar.CalendarService;
import com.pythontopia.world.calendar.dto.CalendarTodayResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * R-12 W2-NPC：把 NPC 对话模板里出现的 {@code {{stats.*}}} / {@code {{profileAdaptive:..}}} /
 * {@code {{giftFavorite:..}}} 全部用真实数据填充。
 *
 * <p>聚合源：
 *
 * <ul>
 *   <li>{@link DialogContextRepository} — practice_submission / judge_result / kc_mastery /
 *       kc_mastery_event / crop_tile_state / course_kc / course_chapter / course_page /
 *       course_problem / classrooms / classroom_members / classroom_quest / world_session /
 *       npc_gift_log；
 *   <li>{@link ProfileRepository} — learner_profile（推断 {@code profileType}）；
 *   <li>{@link CalendarService} — 当前 in-game day / season（季节中文化、daily problem 种子）；
 *   <li>{@link UserRepository} — display_name 兜底。
 * </ul>
 *
 * <p>所有字段缺数据时回退到空字符串或合理零值，模板渲染器在拿到字符串后即可直接拼接，
 * 不会再出现 {@code {{stats.xxx}}} 字面量泄露到 UI 的情况。
 */
@Service
public class DialogContextService {

  private static final Logger log = LoggerFactory.getLogger(DialogContextService.class);

  /** 班级"按节奏走"阈值：平均掌握度 ≥ 0.5。 */
  private static final BigDecimal ON_TRACK_THRESHOLD = new BigDecimal("0.5");

  /** giftJustGiven 判定窗口：近 24 小时给该 NPC 送过 like / love。 */
  private static final int GIFT_RECENT_HOURS = 24;

  private final DialogContextRepository repository;
  private final ProfileRepository profileRepository;
  private final CalendarService calendarService;
  private final UserRepository userRepository;

  public DialogContextService(
      DialogContextRepository repository,
      ProfileRepository profileRepository,
      CalendarService calendarService,
      UserRepository userRepository) {
    this.repository = repository;
    this.profileRepository = profileRepository;
    this.calendarService = calendarService;
    this.userRepository = userRepository;
  }

  /**
   * 聚合对话上下文。{@code npcKey} 仅用于 {@code giftJustGiven} 判定，可选；
   * 缺省则按 false 处理（保守走 B 分支）。
   */
  public DialogContextDto buildContext(Long userId, String username, String npcKey) {
    OffsetDateTime now = OffsetDateTime.now();
    OffsetDateTime weekStart = now.minusDays(7);
    OffsetDateTime twoWeeksAgo = now.minusDays(14);
    OffsetDateTime monthAgo = now.minusDays(30);

    Map<String, String> stats = new LinkedHashMap<>();

    // 本周/上周/差值练习量
    int exercisesThisWeek = repository.countSubmissions(userId, weekStart, now);
    int exercisesLastWeek = repository.countSubmissions(userId, twoWeeksAgo, weekStart);
    stats.put("exercisesThisWeek", Integer.toString(exercisesThisWeek));
    stats.put("exercisesLastWeek", Integer.toString(exercisesLastWeek));
    stats.put("exercisesDelta", formatSignedInt(exercisesThisWeek - exercisesLastWeek));

    int failedThisWeek = repository.countFailedSubmissions(userId, weekStart, now);
    stats.put("failedSubmissionsThisWeek", Integer.toString(failedThisWeek));

    // 解锁 KC 统计
    int kcThisWeek = repository.countUnlockedCropsInRange(userId, weekStart, now);
    int kcLastWeek = repository.countUnlockedCropsInRange(userId, twoWeeksAgo, weekStart);
    stats.put("kcUnlockedThisWeek", Integer.toString(kcThisWeek));
    stats.put("kcUnlockedLastWeek", Integer.toString(kcLastWeek));

    List<String> unlockedKcs = repository.findUnlockedKcNames(userId, 3);
    stats.put("kcUnlocked", Integer.toString(unlockedKcs.size()));
    stats.put("kcUnlockedListShort", unlockedKcs.isEmpty() ? "—" : String.join("、", unlockedKcs));
    repository
        .findLatestUnlockedKcName(userId)
        .ifPresentOrElse(
            name -> stats.put("latestKcName", name),
            () -> stats.put("latestKcName", unlockedKcs.isEmpty() ? "—" : unlockedKcs.get(0)));
    repository.findNextLockedKc(userId).ifPresentOrElse(
        name -> stats.put("nextKcName", name),
        () -> stats.put("nextKcName", "下一道关"));
    repository.findWitheredKcName(userId).ifPresentOrElse(
        name -> stats.put("wiltedKcName", name),
        () -> stats.put("wiltedKcName", "暂无枯萎地块"));
    repository.findHarvestKcName(userId).ifPresentOrElse(
        name -> stats.put("harvestKcName", name),
        () -> stats.put("harvestKcName", "尚未成熟的地块"));

    // 本周最难关 + 全时段错误模式
    List<KcFailureRow> toughest = repository.findToughestKcs(userId, weekStart, now, 1);
    stats.put(
        "toughestKcNameThisWeek",
        toughest.isEmpty() ? "—" : toughest.get(0).kcName());

    List<VerdictCountRow> verdicts = repository.findVerdictDistribution(userId, 5);
    int totalVerdicts = verdicts.stream().mapToInt(VerdictCountRow::count).sum();
    if (verdicts.isEmpty()) {
      stats.put("topMistakePattern", "—");
      stats.put("topMistakeRate", "0");
    } else {
      VerdictCountRow top = verdicts.get(0);
      stats.put("topMistakePattern", verdictToHuman(top.verdict()));
      int rate = totalVerdicts == 0 ? 0 : Math.round((top.count() * 100f) / totalVerdicts);
      stats.put("topMistakeRate", Integer.toString(rate));
    }

    // 最近失败题
    LastFailureRow lastFail = repository.findLastFailure(userId).orElse(null);
    if (lastFail == null) {
      stats.put("lastFailedProblemTitle", "—");
      stats.put("lastFailedErrorType", "—");
      stats.put("lastFailedMineLevel", "1");
      stats.put("anxiousLastFailProblemNo", "—");
      stats.put("anxiousLastFailError", "—");
    } else {
      stats.put("lastFailedProblemTitle", nullSafe(lastFail.title(), "—"));
      stats.put("lastFailedErrorType", verdictToHuman(lastFail.verdict()));
      stats.put("lastFailedMineLevel", Integer.toString(Math.min(5, 1 + failedThisWeek / 3)));
      stats.put("anxiousLastFailProblemNo", lastFail.problemId() == null ? "—" : lastFail.problemId().toString());
      stats.put("anxiousLastFailError", verdictToHuman(lastFail.verdict()));
    }

    // 掌握度 / 最低掌握 KC / 当前章节
    KcMasteryRow lowest = repository.findLowestMasteryKc(userId).orElse(null);
    String lowestKcCode = lowest == null ? null : lowest.kcCode();
    stats.put("lowestMasteryKcName", lowest == null ? "—" : lowest.kcName());

    Optional<BigDecimal> avgMastery = repository.findAverageMastery(userId);
    int masteryAvgPercent =
        avgMastery
            .map(v -> v.multiply(new BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).intValue())
            .orElse(0);
    stats.put("masteryAvg", Integer.toString(masteryAvgPercent));

    BigDecimal deltaSum =
        repository.findMasteryDeltaSum(userId, weekStart, now).orElse(BigDecimal.ZERO);
    int deltaPercent =
        deltaSum.multiply(new BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).intValue();
    stats.put("masteryDelta", formatSignedInt(deltaPercent));

    int currentChapterOrder = repository.findCurrentChapterOrder(userId).orElse(1);
    stats.put("currentChapterOrder", Integer.toString(currentChapterOrder));

    KcChapterRow nextChapter =
        repository.findRecommendChapter(userId, currentChapterOrder).orElse(null);
    if (nextChapter == null) {
      // 没有下一章可推：用 currentChapter 自身或第 1 章兜底
      stats.put("recommendChapterOrder", Integer.toString(Math.max(1, currentChapterOrder)));
      stats.put("recommendChapterTitle", "Python 入门");
      stats.put("recommendPageStart", "—");
      stats.put("recommendPageEnd", "—");
    } else {
      stats.put(
          "recommendChapterOrder",
          nextChapter.chapterOrder() == null ? "1" : nextChapter.chapterOrder().toString());
      stats.put("recommendChapterTitle", nullSafe(nextChapter.title(), "下一章节"));
      stats.put("recommendPageStart", nextChapter.pageMin() == null ? "—" : nextChapter.pageMin().toString());
      stats.put("recommendPageEnd", nextChapter.pageMax() == null ? "—" : nextChapter.pageMax().toString());
    }

    // focus KC = lowestMasteryKc，做 page 引用
    KcChapterRow focusChapter =
        lowestKcCode == null
            ? null
            : repository.findKcChapter(lowestKcCode).orElse(null);
    stats.put("focusKcName", lowest == null ? "—" : lowest.kcName());
    stats.put(
        "focusKcPageStart",
        focusChapter == null || focusChapter.pageMin() == null
            ? "—"
            : focusChapter.pageMin().toString());
    stats.put(
        "focusKcPageEnd",
        focusChapter == null || focusChapter.pageMax() == null
            ? "—"
            : focusChapter.pageMax().toString());
    stats.put(
        "focusKcExamplePage",
        focusChapter == null || focusChapter.examplePage() == null
            ? "—"
            : focusChapter.examplePage().toString());

    // 题目数据 + 推荐
    int problemsSolved = repository.countDistinctPassedProblems(userId);
    stats.put("problemsSolved", Integer.toString(problemsSolved));
    int allSubs = repository.countAllSubmissions(userId);
    int passedSubs = repository.countPassedSubmissions(userId);
    int firstAttemptRate = allSubs == 0 ? 0 : Math.round((passedSubs * 100f) / allSubs);
    stats.put("firstAttemptRate", Integer.toString(firstAttemptRate));

    long entryId = repository.findEntryProblemId().orElse(1L);
    stats.put("entryProblemNo", Long.toString(entryId));

    ProblemRow recommended = repository.findRecommendProblem(lowestKcCode).orElse(null);
    if (recommended == null) {
      stats.put("recommendProblemNo", Long.toString(entryId));
      stats.put("recommendProblemDifficulty", "BEGINNER");
    } else {
      stats.put("recommendProblemNo", recommended.id().toString());
      stats.put("recommendProblemDifficulty", recommended.difficulty());
    }
    long challengeId =
        repository
            .findChallengeProblemId()
            .orElseGet(() -> recommended == null ? entryId : recommended.id());
    stats.put("challengeProblemNo", Long.toString(challengeId));

    // 每日题：用日历 dayIndex 做种子，保证同日稳定
    CalendarTodayResponse calendar = safeCalendar(userId);
    int daySeed = calendar == null ? (int) (now.toEpochSecond() / 86400) : calendar.dayIndex();
    long dailyChallenge =
        repository
            .findDailyProblemByDifficulty("ADVANCED", daySeed + (int) (userId % 7))
            .orElse(challengeId);
    long dailyReview =
        repository
            .findDailyProblemByDifficulty("BEGINNER", daySeed + (int) (userId % 5))
            .orElse(entryId);
    stats.put("dailyChallengeProblemNo", Long.toString(dailyChallenge));
    stats.put("dailyReviewProblemNo", Long.toString(dailyReview));

    // 季节 + 能量 + 商店
    if (calendar == null) {
      stats.put("seasonName", "春");
    } else {
      stats.put("seasonName", seasonToChinese(calendar.season()));
    }
    EnergyRow energy = repository.findEnergy(userId).orElse(new EnergyRow(100, 100));
    stats.put("energyCurrent", Integer.toString(energy.current()));
    stats.put("energyMax", Integer.toString(energy.max()));

    // 商店周限购：3 本基础 - 本周已购买（暂无库存表，按本周 mastery 事件粗估）
    stats.put(
        "merchantWeeklySpecialLeft",
        Integer.toString(Math.max(0, 3 - Math.min(3, kcThisWeek / 2))));

    // 班级数据
    Long classroomId = repository.findStudentPrimaryClassroom(userId).orElse(null);
    if (classroomId == null) {
      stats.put("classmateCount", "—");
      stats.put("classmatesOnTrack", "—");
      stats.put("classRankBand", "—");
      stats.put("classQuestNewCount", "0");
      stats.put("classFocusKcName", "—");
      stats.put("classFinalProblemNo", "—");
      stats.put("classWeeklyHotspotKc", "—");
      stats.put("peerDiligentExercisesWeek", "—");
      stats.put("peerDiligentMastery", "—");
      stats.put("peerLateStarterMastery", "—");
      stats.put("masteryDeltaVsLateStarter", "—");
      stats.put("peerLateStarterMonthExercises", "—");
    } else {
      int memberCount = repository.countActiveMembers(classroomId);
      int onTrack = repository.countOnTrackMembers(classroomId, ON_TRACK_THRESHOLD);
      stats.put("classmateCount", Integer.toString(memberCount));
      stats.put("classmatesOnTrack", Integer.toString(onTrack));
      stats.put(
          "classRankBand",
          rankBand(
              repository.findStudentClassroomRank(classroomId, userId).orElse(memberCount),
              memberCount));
      stats.put(
          "classQuestNewCount",
          Integer.toString(repository.countNewClassroomQuests(classroomId, weekStart)));
      stats.put("classFocusKcName", lowest == null ? "—" : lowest.kcName());
      stats.put(
          "classFinalProblemNo",
          repository
              .findClassFinalProblemId(classroomId)
              .map(Object::toString)
              .orElse("—"));
      stats.put(
          "classWeeklyHotspotKc",
          repository
              .findClassWeeklyHotspot(classroomId, weekStart, now)
              .orElse("—"));
      PeerRow diligent =
          repository.findMostDiligentPeer(classroomId, userId, weekStart, now).orElse(null);
      if (diligent == null) {
        stats.put("peerDiligentExercisesWeek", Integer.toString(Math.max(exercisesThisWeek, 1)));
        stats.put("peerDiligentMastery", Integer.toString(masteryAvgPercent));
      } else {
        stats.put("peerDiligentExercisesWeek", Integer.toString(diligent.metric()));
        stats.put(
            "peerDiligentMastery",
            Integer.toString(
                diligent
                    .avgMastery()
                    .multiply(new BigDecimal(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .intValue()));
      }
      PeerRow lateStarter =
          repository.findLateStarterPeer(classroomId, userId, monthAgo).orElse(null);
      if (lateStarter == null) {
        stats.put("peerLateStarterMastery", "—");
        stats.put("masteryDeltaVsLateStarter", "—");
        stats.put("peerLateStarterMonthExercises", "—");
      } else {
        int lateMasteryPercent =
            lateStarter
                .avgMastery()
                .multiply(new BigDecimal(100))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
        stats.put("peerLateStarterMastery", Integer.toString(lateMasteryPercent));
        stats.put(
            "masteryDeltaVsLateStarter",
            formatSignedInt(masteryAvgPercent - lateMasteryPercent));
        stats.put("peerLateStarterMonthExercises", Integer.toString(lateStarter.metric()));
      }
    }

    // 玩家显示名
    String displayName = resolveDisplayName(userId, username);

    // profile 类型 + gift just given
    LearnerProfileDto profile = profileRepository.findProfile(userId).orElse(null);
    String profileType = inferProfileType(profile);
    boolean giftJustGiven =
        npcKey != null && repository.hasFavoriteGiftRecently(userId, npcKey, GIFT_RECENT_HOURS);

    // 把 profileType 同步暴露到 stats（少数对话直接引用 stats.profileType）
    stats.put("profileType", profileType);

    return new DialogContextDto(displayName, profileType, giftJustGiven, Map.copyOf(stats));
  }

  private String resolveDisplayName(Long userId, String fallbackUsername) {
    UserAccount user = userRepository.findById(userId).orElse(null);
    if (user == null) {
      return fallbackUsername == null ? "学习者" : fallbackUsername;
    }
    if (user.displayName() != null && !user.displayName().isBlank()) {
      return user.displayName();
    }
    return user.username() == null ? "学习者" : user.username();
  }

  private CalendarTodayResponse safeCalendar(Long userId) {
    try {
      return calendarService.today(userId);
    } catch (RuntimeException ex) {
      // 新用户尚未初始化世界、SQL 失败等：用当前真实日期兜底，不让接口因日历缺失而 500。
      log.debug("calendar unavailable for user {}: {}", userId, ex.getMessage());
      return null;
    }
  }

  /**
   * 用 learner_profile.cognitive_style 判定。{@code sequentialVsHolistic ≥ 0.55} 偏整体 / 实践型；
   * {@code ≤ 0.45} 偏序列 / 谨慎型；其余落入 balanced 但仍归为 cautious（保守走 B 分支，模板中 B
   * 通常是更稳的入门建议）。profile 缺失同样按 cautious 处理。
   */
  private String inferProfileType(LearnerProfileDto profile) {
    if (profile == null) {
      return DialogContextDto.PROFILE_CAUTIOUS;
    }
    CognitiveStyleDto style = profile.cognitiveStyle();
    if (style == null || style.sequentialVsHolistic() == null) {
      return DialogContextDto.PROFILE_CAUTIOUS;
    }
    double value = style.sequentialVsHolistic();
    if (value >= 0.55) {
      return DialogContextDto.PROFILE_PRACTICAL;
    }
    return DialogContextDto.PROFILE_CAUTIOUS;
  }

  private static String formatSignedInt(int value) {
    if (value > 0) {
      return "+" + value;
    }
    return Integer.toString(value);
  }

  private static String nullSafe(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private static String seasonToChinese(String season) {
    if (season == null) {
      return "春";
    }
    return switch (season.toLowerCase(Locale.ROOT)) {
      case "spring" -> "春";
      case "summer" -> "夏";
      case "autumn", "fall" -> "秋";
      case "winter" -> "冬";
      default -> season;
    };
  }

  /** verdict -> 中文短语，让 NPC 对话不会蹦出 'RUNTIME_ERROR' 这种生硬字面量。 */
  private static String verdictToHuman(String verdict) {
    if (verdict == null) {
      return "—";
    }
    return switch (verdict.toUpperCase(Locale.ROOT)) {
      case "AC" -> "通过";
      case "WA", "WRONG_ANSWER" -> "输出错误";
      case "RE", "RUNTIME_ERROR" -> "运行时报错";
      case "TLE", "TIME_LIMIT_EXCEEDED" -> "超时";
      case "MLE", "MEMORY_LIMIT_EXCEEDED" -> "内存超限";
      case "CE", "COMPILE_ERROR" -> "编译/语法错误";
      case "SE", "SYSTEM_ERROR" -> "系统异常";
      case "INDENTATIONERROR" -> "缩进错误";
      default -> verdict.toLowerCase(Locale.ROOT).replace('_', ' ');
    };
  }

  /** 班级排名段位：T1 头部 / T2 上半 / T3 中段 / T4 末段。 */
  private static String rankBand(int rank, int total) {
    if (total <= 0) {
      return "—";
    }
    double ratio = (double) rank / total;
    if (ratio <= 0.25) {
      return "前 25%";
    }
    if (ratio <= 0.5) {
      return "前 50%";
    }
    if (ratio <= 0.75) {
      return "后 50%";
    }
    return "后 25%";
  }

  /** 仅供测试观察：暴露今日日期 ZoneId，便于日历缺失时回归。 */
  @SuppressWarnings("unused")
  static LocalDate utcToday() {
    return LocalDate.now(ZoneId.of("UTC"));
  }
}
