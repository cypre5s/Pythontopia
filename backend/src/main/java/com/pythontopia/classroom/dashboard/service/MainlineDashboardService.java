package com.pythontopia.classroom.dashboard.service;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.classroom.dashboard.dto.ChapterTestStatsDto;
import com.pythontopia.classroom.dashboard.dto.ChapterTestStatsDto.ChapterTestRow;
import com.pythontopia.classroom.dashboard.dto.ChapterTestStatsDto.ScoreDistribution;
import com.pythontopia.classroom.dashboard.dto.EndlessActivityDto;
import com.pythontopia.classroom.dashboard.dto.EndlessActivityDto.ChapterEndless;
import com.pythontopia.classroom.dashboard.dto.EndlessActivityDto.DailyEndless;
import com.pythontopia.classroom.dashboard.dto.MainlineProgressMatrixDto;
import com.pythontopia.classroom.dashboard.dto.MainlineProgressMatrixDto.ChapterCell;
import com.pythontopia.classroom.dashboard.dto.MainlineProgressMatrixDto.ChapterStageCount;
import com.pythontopia.classroom.dashboard.dto.MainlineProgressMatrixDto.MemberProgressRow;
import com.pythontopia.classroom.dashboard.dto.MainlineStarMatrixDto;
import com.pythontopia.classroom.dashboard.dto.MainlineStarMatrixDto.ChapterStarRow;
import com.pythontopia.classroom.dashboard.dto.MainlineStarMatrixDto.StudentStarTotal;
import com.pythontopia.classroom.dashboard.repository.MainlineDashboardRepository;
import com.pythontopia.classroom.dashboard.repository.MainlineDashboardRepository.ChapterStarBucketRow;
import com.pythontopia.classroom.dashboard.repository.MainlineDashboardRepository.ChapterTestScoreRow;
import com.pythontopia.classroom.dashboard.repository.MainlineDashboardRepository.EndlessRecoveryRow;
import com.pythontopia.classroom.dashboard.repository.MainlineDashboardRepository.MemberChapterProgressRow;
import com.pythontopia.classroom.dashboard.repository.MainlineDashboardRepository.StageCountRow;
import com.pythontopia.classroom.dashboard.repository.MainlineDashboardRepository.StudentStarTotalRow;
import com.pythontopia.classroom.security.ClassroomGuard;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * R-15 主线教师看板聚合服务（§9 的 4 个教师端点）。在 {@link MainlineDashboardRepository} 的原始聚合行之上
 * 计算星级矩阵 / 章测箱线 / 无尽活跃 / 进度矩阵，并加 <b>5 分钟 TTL 内存缓存</b>（§11.4：快照思路，
 * 50 人班 < 300ms，避免 N+1）。
 *
 * <p><b>红线</b>：每个端点（含缓存命中）先经 {@link ClassroomGuard#requireOwner} 做角色 + 归属校验，
 * 学生 / 非归属教师一律 {@code FORBIDDEN}，且被拒时绝不触达聚合仓储。
 */
@Service
public class MainlineDashboardService {

  /** 看板聚合缓存 TTL：5 分钟（§11.4）。 */
  public static final long CACHE_TTL_MS = 5L * 60L * 1000L;
  /** 全部章节固定 7 章（packId=43）。 */
  private static final int CHAPTERS = 7;
  /** 总星排行 TopN。 */
  private static final int TOP_STUDENTS = 10;
  /** 无尽活跃统计窗口（天）。 */
  private static final int ENDLESS_WINDOW_DAYS = 7;
  /** 章测 1★ 通过阈值（§5）。 */
  private static final int TEST_PASS_SCORE = 60;

  private final MainlineDashboardRepository repository;
  private final ClassroomGuard classroomGuard;
  private final LongSupplier clockMillis;
  private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

  @Autowired
  public MainlineDashboardService(
      MainlineDashboardRepository repository, ClassroomGuard classroomGuard) {
    this(repository, classroomGuard, System::currentTimeMillis);
  }

  /** 测试用构造器：注入可控时钟以验证 TTL 缓存命中 / 过期行为（非 Spring 注入路径）。 */
  public MainlineDashboardService(
      MainlineDashboardRepository repository,
      ClassroomGuard classroomGuard,
      LongSupplier clockMillis) {
    this.repository = repository;
    this.classroomGuard = classroomGuard;
    this.clockMillis = clockMillis;
  }

  // ── §9 端点（TEACHER/ADMIN + ClassroomGuard） ──

  /** 每章 0/1/2/3★ 人数矩阵 + 总星 TopN。 */
  public MainlineStarMatrixDto mainlineStars(CurrentUserPrincipal user, Long classroomId) {
    classroomGuard.requireOwner(user, classroomId);
    return cached("mainline-stars", classroomId, () -> computeMainlineStars(classroomId));
  }

  /** 每章参试人数 / 提交次数 / 通过率 / 分数箱线分布。 */
  public ChapterTestStatsDto chapterTests(CurrentUserPrincipal user, Long classroomId) {
    classroomGuard.requireOwner(user, classroomId);
    return cached("chapter-tests", classroomId, () -> computeChapterTests(classroomId));
  }

  /** 近 7 日无尽补星轮次 / 补星数 / 活跃人数（按天 + 按章）。 */
  public EndlessActivityDto endlessActivity(CurrentUserPrincipal user, Long classroomId) {
    classroomGuard.requireOwner(user, classroomId);
    return cached("endless-activity", classroomId, () -> computeEndlessActivity(classroomId));
  }

  /** 成员 × 章节 的 stage 完成度矩阵。 */
  public MainlineProgressMatrixDto mainlineProgress(CurrentUserPrincipal user, Long classroomId) {
    classroomGuard.requireOwner(user, classroomId);
    return cached("mainline-progress", classroomId, () -> computeMainlineProgress(classroomId));
  }

  // ── 聚合计算（纯函数式，仅依赖仓储原始行；便于 mock 单测） ──

  private MainlineStarMatrixDto computeMainlineStars(Long classroomId) {
    List<StudentStarTotalRow> totals = repository.findStudentStarTotals(classroomId);
    int totalStudents = totals.size();
    List<ChapterStarBucketRow> buckets = repository.findChapterStarBuckets(classroomId);

    // chapter -> {1★ cnt, 2★ cnt, 3★ cnt}
    Map<Integer, int[]> byChapter = new HashMap<>();
    for (ChapterStarBucketRow row : buckets) {
      if (row.stars() < 1 || row.stars() > 3) {
        continue;
      }
      byChapter.computeIfAbsent(row.chapter(), k -> new int[3])[row.stars() - 1] += row.count();
    }

    List<ChapterStarRow> chapters = new ArrayList<>(CHAPTERS);
    for (int ch = 1; ch <= CHAPTERS; ch++) {
      int[] s = byChapter.getOrDefault(ch, new int[3]);
      int one = s[0];
      int two = s[1];
      int three = s[2];
      int zero = Math.max(0, totalStudents - one - two - three);
      double avg = totalStudents == 0 ? 0.0 : round2((one + 2.0 * two + 3.0 * three) / totalStudents);
      chapters.add(new ChapterStarRow(ch, zero, one, two, three, avg));
    }

    List<StudentStarTotal> top = new ArrayList<>();
    for (StudentStarTotalRow row : totals) {
      if (top.size() >= TOP_STUDENTS) {
        break;
      }
      top.add(
          new StudentStarTotal(
              row.studentId(), row.displayName(), row.totalStars(), row.starredChapters()));
    }
    return new MainlineStarMatrixDto(totalStudents, chapters, top);
  }

  private ChapterTestStatsDto computeChapterTests(Long classroomId) {
    int totalStudents = repository.countActiveMembers(classroomId);
    List<ChapterTestScoreRow> rows = repository.findChapterTestScores(classroomId);

    Map<Integer, List<ChapterTestScoreRow>> byChapter = new HashMap<>();
    for (ChapterTestScoreRow row : rows) {
      byChapter.computeIfAbsent(row.chapter(), k -> new ArrayList<>()).add(row);
    }

    List<ChapterTestRow> chapters = new ArrayList<>(CHAPTERS);
    for (int ch = 1; ch <= CHAPTERS; ch++) {
      List<ChapterTestScoreRow> chRows = byChapter.getOrDefault(ch, List.of());
      int participants = chRows.size();
      int attempts = 0;
      int passed = 0;
      List<Integer> bestScores = new ArrayList<>(participants);
      for (ChapterTestScoreRow row : chRows) {
        attempts += row.attempts();
        bestScores.add(row.bestScore());
        if (row.bestScore() >= TEST_PASS_SCORE) {
          passed++;
        }
      }
      double passRate = participants == 0 ? 0.0 : round3((double) passed / participants);
      chapters.add(
          new ChapterTestRow(ch, participants, attempts, passed, passRate, boxplot(bestScores)));
    }
    return new ChapterTestStatsDto(totalStudents, chapters);
  }

  private EndlessActivityDto computeEndlessActivity(Long classroomId) {
    int totalStudents = repository.countActiveMembers(classroomId);
    List<EndlessRecoveryRow> events =
        repository.findEndlessRecoveryEvents(classroomId, ENDLESS_WINDOW_DAYS);

    LocalDate end = LocalDate.now(ZoneOffset.UTC);
    LocalDate start = end.minusDays(ENDLESS_WINDOW_DAYS - 1L);

    Map<LocalDate, Integer> perDay = new HashMap<>();
    Map<Integer, int[]> perChapter = new HashMap<>(); // chapter -> {rounds, distinctStudents}
    Map<Integer, Set<Long>> chapterStudents = new HashMap<>();
    Set<Long> activeStudents = new HashSet<>();
    for (EndlessRecoveryRow e : events) {
      perDay.merge(e.day(), 1, Integer::sum);
      perChapter.computeIfAbsent(e.chapter(), k -> new int[1])[0] += 1;
      chapterStudents.computeIfAbsent(e.chapter(), k -> new HashSet<>()).add(e.userId());
      activeStudents.add(e.userId());
    }

    List<DailyEndless> daily = new ArrayList<>(ENDLESS_WINDOW_DAYS);
    for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
      int count = perDay.getOrDefault(d, 0);
      daily.add(new DailyEndless(d, count, count));
    }

    List<ChapterEndless> chapters = new ArrayList<>(CHAPTERS);
    for (int ch = 1; ch <= CHAPTERS; ch++) {
      int rounds = perChapter.containsKey(ch) ? perChapter.get(ch)[0] : 0;
      int students = chapterStudents.containsKey(ch) ? chapterStudents.get(ch).size() : 0;
      chapters.add(new ChapterEndless(ch, rounds, rounds, students));
    }

    int recoveryTotal = events.size();
    return new EndlessActivityDto(
        start,
        end,
        totalStudents,
        activeStudents.size(),
        recoveryTotal,
        recoveryTotal,
        null, // accuracyPct：§3.2 无无尽轮次明细表，逐轮正确率暂未采集（见 DTO 注释 / 交付报告）
        daily,
        chapters);
  }

  private MainlineProgressMatrixDto computeMainlineProgress(Long classroomId) {
    Map<Integer, Integer> stageCounts = new LinkedHashMap<>();
    int totalStages = 0;
    for (StageCountRow row : repository.findStageCountsPerChapter()) {
      stageCounts.put(row.chapter(), row.stageCount());
      totalStages += row.stageCount();
    }

    Map<Long, Integer> starByStudent = new HashMap<>();
    for (StudentStarTotalRow row : repository.findStudentStarTotals(classroomId)) {
      starByStudent.put(row.studentId(), row.totalStars());
    }

    // student -> (chapter -> completed)；保留出现顺序，零进度成员（chapter=null）也登记。
    Map<Long, MemberAccumulator> members = new LinkedHashMap<>();
    for (MemberChapterProgressRow row : repository.findMemberChapterProgress(classroomId)) {
      MemberAccumulator acc =
          members.computeIfAbsent(
              row.studentId(), k -> new MemberAccumulator(row.studentId(), row.displayName()));
      if (row.chapter() != null && row.completed() > 0) {
        acc.completedByChapter.merge(row.chapter(), row.completed(), Integer::sum);
      }
    }

    List<ChapterStageCount> chapterStageCounts = new ArrayList<>(CHAPTERS);
    for (int ch = 1; ch <= CHAPTERS; ch++) {
      chapterStageCounts.add(new ChapterStageCount(ch, stageCounts.getOrDefault(ch, 0)));
    }

    List<MemberProgressRow> memberRows = new ArrayList<>(members.size());
    for (MemberAccumulator acc : members.values()) {
      List<ChapterCell> cells = new ArrayList<>(CHAPTERS);
      int completedTotal = 0;
      for (int ch = 1; ch <= CHAPTERS; ch++) {
        int done = acc.completedByChapter.getOrDefault(ch, 0);
        int total = stageCounts.getOrDefault(ch, 0);
        completedTotal += done;
        cells.add(new ChapterCell(ch, done, total));
      }
      memberRows.add(
          new MemberProgressRow(
              acc.studentId,
              acc.displayName,
              completedTotal,
              starByStudent.getOrDefault(acc.studentId, 0),
              cells));
    }
    memberRows.sort(
        (a, b) -> {
          int byCompleted = Integer.compare(b.completedStages(), a.completedStages());
          if (byCompleted != 0) {
            return byCompleted;
          }
          int byStars = Integer.compare(b.totalStars(), a.totalStars());
          if (byStars != 0) {
            return byStars;
          }
          return Long.compare(a.studentId(), b.studentId());
        });

    return new MainlineProgressMatrixDto(
        members.size(), totalStages, chapterStageCounts, memberRows);
  }

  // ── 箱线五数概括（基于每生最高分；纯函数，便于直接单测） ──

  /** 由整数分数列表计算箱线五数概括（min/q1/median/q3/max）；空列表返回 {@code count=0} 的全空分布。 */
  public static ScoreDistribution boxplot(List<Integer> scores) {
    if (scores == null || scores.isEmpty()) {
      return new ScoreDistribution(0, null, null, null, null, null);
    }
    int[] sorted = scores.stream().mapToInt(Integer::intValue).sorted().toArray();
    return new ScoreDistribution(
        sorted.length,
        sorted[0],
        percentile(sorted, 0.25),
        percentile(sorted, 0.50),
        percentile(sorted, 0.75),
        sorted[sorted.length - 1]);
  }

  /** 线性插值分位数（NumPy 默认 type-7：rank = p·(n−1)，相邻取整插值），四舍五入到整分。 */
  public static int percentile(int[] sorted, double p) {
    if (sorted.length == 1) {
      return sorted[0];
    }
    double rank = p * (sorted.length - 1);
    int lo = (int) Math.floor(rank);
    int hi = (int) Math.ceil(rank);
    if (lo == hi) {
      return sorted[lo];
    }
    double frac = rank - lo;
    return (int) Math.round(sorted[lo] + (sorted[hi] - sorted[lo]) * frac);
  }

  // ── TTL 缓存 ──

  @SuppressWarnings("unchecked")
  private <T> T cached(String endpoint, Long classroomId, Supplier<T> loader) {
    String key = endpoint + ":" + classroomId;
    long now = clockMillis.getAsLong();
    CacheEntry entry = cache.get(key);
    if (entry != null && entry.expiresAt() > now) {
      return (T) entry.payload();
    }
    T value = loader.get();
    cache.put(key, new CacheEntry(now + CACHE_TTL_MS, value));
    return value;
  }

  private static double round2(double v) {
    return Math.round(v * 100.0) / 100.0;
  }

  private static double round3(double v) {
    return Math.round(v * 1000.0) / 1000.0;
  }

  private record CacheEntry(long expiresAt, Object payload) {}

  private static final class MemberAccumulator {
    final long studentId;
    final String displayName;
    final Map<Integer, Integer> completedByChapter = new HashMap<>();

    MemberAccumulator(long studentId, String displayName) {
      this.studentId = studentId;
      this.displayName = displayName;
    }
  }
}
