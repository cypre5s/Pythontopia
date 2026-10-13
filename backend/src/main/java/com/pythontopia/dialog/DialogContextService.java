package com.pythontopia.dialog;

import com.fasterxml.jackson.databind.JsonNode;
import com.pythontopia.auth.domain.UserAccount;
import com.pythontopia.auth.repository.UserRepository;
import com.pythontopia.dialog.DialogContextRepository.KcChapterRow;
import com.pythontopia.dialog.DialogContextRepository.KcFailureRow;
import com.pythontopia.dialog.DialogContextRepository.KcMasteryRow;
import com.pythontopia.dialog.DialogContextRepository.LastFailureRow;
import com.pythontopia.dialog.DialogContextRepository.VerdictCountRow;
import com.pythontopia.learner.profile.repository.ProfileRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 把 NPC 对话模板里的 {@code {{stats.*}}} / {@code {{profileAdaptive:..}}} 占位符全部用真实数据填充。
 *
 * <p>聚合源：
 *
 * <ul>
 *   <li>{@link DialogContextRepository} — practice_submission / judge_result / kc_mastery /
 *       course_kc / course_chapter / course_page / course_problem / classrooms /
 *       classroom_members / classroom_quest；
 *   <li>{@link ProfileRepository} — learner_profile（推断 {@code profileType} / {@code cognitiveStyle}）；
 *   <li>{@link UserRepository} — display_name 兜底。
 * </ul>
 *
 * <p><b>产出集 = 消费集</b>：这里产出的每个 {@code stats.*} key 都被 {@code data/npcs/&#42;/dialog.json}
 * 的模板真实引用（{@code profileType} / {@code cognitiveStyle} 另外镜像到 DTO 顶层驱动
 * {@code profileAdaptive} / {@code styleAdaptive} 分支）。历史上这里多产出 27 个从未被任何模板引用的
 * key（商店周限购、作物枯萎/成熟、每日题、能量、同伴对比等 R-14 已删玩法的残留），每次对话都白跑十余条
 * SQL，已随本次收敛一并裁掉——新增 key 前请先确认模板里确有对应占位符。
 *
 * <p>所有字段缺数据时回退到空字符串或合理零值，模板渲染器拿到字符串即可直接拼接，
 * 不会出现 {@code {{stats.xxx}}} 字面量泄露到 UI。
 */
@Service
public class DialogContextService {

  private static final Logger log = LoggerFactory.getLogger(DialogContextService.class);

  /** 班级"按节奏走"阈值：平均掌握度 ≥ 0.5。 */
  private static final BigDecimal ON_TRACK_THRESHOLD = new BigDecimal("0.5");

  private final DialogContextRepository repository;
  private final ProfileRepository profileRepository;
  private final UserRepository userRepository;

  public DialogContextService(
      DialogContextRepository repository,
      ProfileRepository profileRepository,
      UserRepository userRepository) {
    this.repository = repository;
    this.profileRepository = profileRepository;
    this.userRepository = userRepository;
  }

  /** 聚合对话上下文。 */
  public DialogContextDto buildContext(Long userId, String username) {
    OffsetDateTime now = OffsetDateTime.now();
    OffsetDateTime weekStart = now.minusDays(7);
    OffsetDateTime twoWeeksAgo = now.minusDays(14);

    Map<String, String> stats = new LinkedHashMap<>();

    // 本周/上周练习量
    int exercisesThisWeek = repository.countSubmissions(userId, weekStart, now);
    int exercisesLastWeek = repository.countSubmissions(userId, twoWeeksAgo, weekStart);
    stats.put("exercisesThisWeek", Integer.toString(exercisesThisWeek));
    stats.put("exercisesLastWeek", Integer.toString(exercisesLastWeek));

    int failedThisWeek = repository.countFailedSubmissions(userId, weekStart, now);
    stats.put("failedSubmissionsThisWeek", Integer.toString(failedThisWeek));

    // 解锁 KC 统计
    int kcThisWeek = repository.countKcReachedThresholdInRange(userId, weekStart, now);
    int kcLastWeek = repository.countKcReachedThresholdInRange(userId, twoWeeksAgo, weekStart);
    stats.put("kcUnlockedThisWeek", Integer.toString(kcThisWeek));
    stats.put("kcUnlockedLastWeek", Integer.toString(kcLastWeek));

    stats.put("kcUnlocked", Integer.toString(repository.findUnlockedKcNames(userId, 3).size()));

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
    } else {
      stats.put("lastFailedProblemTitle", nullSafe(lastFail.title(), "—"));
      stats.put("lastFailedErrorType", verdictToHuman(lastFail.verdict()));
      stats.put("lastFailedMineLevel", Integer.toString(Math.min(5, 1 + failedThisWeek / 3)));
    }

    // 掌握度 / 最低掌握 KC / 当前章节
    KcMasteryRow lowest = repository.findLowestMasteryKc(userId).orElse(null);
    String lowestKcCode = lowest == null ? null : lowest.kcCode();

    Optional<BigDecimal> avgMastery = repository.findAverageMastery(userId);
    int masteryAvgPercent =
        avgMastery
            .map(v -> v.multiply(new BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).intValue())
            .orElse(0);
    stats.put("masteryAvg", Integer.toString(masteryAvgPercent));

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
    }

    String displayName = resolveDisplayName(userId, username);

    // The legacy template slot now follows an explicit media preference, never a learner-type inference.
    String profileType = DialogContextDto.PROFILE_CAUTIOUS;
    String cognitiveStyle = inferPresentationPreference(userId);

    // profileType / cognitiveStyle 同步暴露到 stats：少数对话直接引用 stats.* 字面量
    stats.put("profileType", profileType);
    stats.put("cognitiveStyle", cognitiveStyle);

    return new DialogContextDto(
        displayName, profileType, cognitiveStyle, Map.copyOf(stats));
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

  /** Read an explicit media preference for legacy dialog templates; never read stored cognitive-style labels. */
  private String inferPresentationPreference(Long userId) {
    try {
      JsonNode dims = profileRepository.findDimensions(userId).orElse(null);
      if (dims == null) {
        return DialogContextDto.STYLE_UNKNOWN;
      }
      JsonNode tags = dims.path("learning_preference").path("tags");
      if (!tags.isArray() || tags.isEmpty()) {
        return DialogContextDto.STYLE_UNKNOWN;
      }
      // 最新/主导 = 最后追加的 tag（PassiveProfileService 按时间顺序 append）。
      String latest = tags.get(tags.size() - 1).asText(null);
      return normalizePresentationPreference(latest);
    } catch (RuntimeException ex) {
      log.debug("learning preference unavailable for user {}: {}", userId, ex.getMessage());
      return DialogContextDto.STYLE_UNKNOWN;
    }
  }

  private static String normalizePresentationPreference(String tag) {
    if (tag == null || tag.isBlank()) {
      return DialogContextDto.STYLE_UNKNOWN;
    }
    String t = tag.trim().toLowerCase(Locale.ROOT);
    return switch (t) {
      case "video", "mindmap" -> DialogContextDto.STYLE_VISUAL;
      case "text" -> DialogContextDto.STYLE_LOGICAL;
      case "code" -> DialogContextDto.STYLE_TRIAL;
      default -> DialogContextDto.STYLE_UNKNOWN;
    };
  }

  private static String nullSafe(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
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

  /** 班级排名段位：前 25% / 前 50% / 后 50% / 后 25%。 */
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
}
