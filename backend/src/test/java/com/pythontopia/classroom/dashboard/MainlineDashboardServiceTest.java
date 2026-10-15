package com.pythontopia.classroom.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.classroom.dashboard.dto.ChapterTestStatsDto;
import com.pythontopia.classroom.dashboard.dto.ChapterTestStatsDto.ChapterTestRow;
import com.pythontopia.classroom.dashboard.dto.ChapterTestStatsDto.ScoreDistribution;
import com.pythontopia.classroom.dashboard.dto.EndlessActivityDto;
import com.pythontopia.classroom.dashboard.dto.EndlessActivityDto.DailyEndless;
import com.pythontopia.classroom.dashboard.dto.MainlineProgressMatrixDto;
import com.pythontopia.classroom.dashboard.dto.MainlineProgressMatrixDto.MemberProgressRow;
import com.pythontopia.classroom.dashboard.dto.MainlineStarMatrixDto;
import com.pythontopia.classroom.dashboard.dto.MainlineStarMatrixDto.ChapterStarRow;
import com.pythontopia.classroom.dashboard.repository.MainlineDashboardRepository;
import com.pythontopia.classroom.dashboard.repository.MainlineDashboardRepository.ChapterStarBucketRow;
import com.pythontopia.classroom.dashboard.repository.MainlineDashboardRepository.ChapterTestScoreRow;
import com.pythontopia.classroom.dashboard.repository.MainlineDashboardRepository.EndlessRecoveryRow;
import com.pythontopia.classroom.dashboard.repository.MainlineDashboardRepository.MemberChapterProgressRow;
import com.pythontopia.classroom.dashboard.repository.MainlineDashboardRepository.StageCountRow;
import com.pythontopia.classroom.dashboard.repository.MainlineDashboardRepository.StudentStarTotalRow;
import com.pythontopia.classroom.dashboard.service.MainlineDashboardService;
import com.pythontopia.classroom.security.ClassroomGuard;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

/**
 * R-15 主线教师看板聚合服务单测：<b>数据层全 mock</b>（§3.2 的 V156/V157 尚未灌库，真库联调延后到 Wave-3）。
 * 覆盖 4 个端点的矩阵/箱线/按天聚合逻辑、星级 0★ 反推、进度零分母处理，以及 5 分钟 TTL 缓存命中/过期。
 */
class MainlineDashboardServiceTest {

  private final MainlineDashboardRepository repository = mock(MainlineDashboardRepository.class);
  private final ClassroomGuard guard = mock(ClassroomGuard.class);
  private final long[] now = {1_000_000L};
  private final LongSupplier clock = () -> now[0];
  private final MainlineDashboardService service =
      new MainlineDashboardService(repository, guard, clock);
  private final CurrentUserPrincipal teacher = new CurrentUserPrincipal(42L, "t01", Role.TEACHER);

  @Test
  void mainlineStarsBuildsHeadcountMatrixAndTopN() {
    when(repository.findStudentStarTotals(9L))
        .thenReturn(
            List.of(
                new StudentStarTotalRow(11L, "Stu A", 7, 3),
                new StudentStarTotalRow(12L, "Stu B", 2, 1),
                new StudentStarTotalRow(13L, "Stu C", 0, 0)));
    when(repository.findChapterStarBuckets(9L))
        .thenReturn(
            List.of(
                new ChapterStarBucketRow(1, 1, 1),
                new ChapterStarBucketRow(1, 2, 1),
                new ChapterStarBucketRow(1, 3, 1),
                new ChapterStarBucketRow(2, 1, 2)));

    MainlineStarMatrixDto dto = service.mainlineStars(teacher, 9L);

    assertThat(dto.totalStudents()).isEqualTo(3);
    assertThat(dto.chapters()).hasSize(7);
    ChapterStarRow ch1 = dto.chapters().get(0);
    assertThat(ch1.chapter()).isEqualTo(1);
    assertThat(ch1.zeroStar()).isZero();
    assertThat(ch1.oneStar()).isEqualTo(1);
    assertThat(ch1.twoStar()).isEqualTo(1);
    assertThat(ch1.threeStar()).isEqualTo(1);
    assertThat(ch1.avgStars()).isEqualTo(2.0);
    ChapterStarRow ch2 = dto.chapters().get(1);
    // 2 名学生各 1★，其余 1 人按 0★ 反推；均星 = 2/3 ≈ 0.67。
    assertThat(ch2.oneStar()).isEqualTo(2);
    assertThat(ch2.zeroStar()).isEqualTo(1);
    assertThat(ch2.avgStars()).isEqualTo(0.67);
    // 无桶章节全部 0★。
    assertThat(dto.chapters().get(6).zeroStar()).isEqualTo(3);
    // TopN 已按总星降序。
    assertThat(dto.topStudents()).hasSize(3);
    assertThat(dto.topStudents().get(0).studentId()).isEqualTo(11L);
    assertThat(dto.topStudents().get(0).totalStars()).isEqualTo(7);
  }

  @Test
  void chapterTestsComputesPassRateAndBoxplotFromBestScores() {
    when(repository.countActiveMembers(9L)).thenReturn(4);
    when(repository.findChapterTestScores(9L))
        .thenReturn(
            List.of(
                new ChapterTestScoreRow(1, 11L, 90, 2),
                new ChapterTestScoreRow(1, 12L, 70, 1),
                new ChapterTestScoreRow(1, 13L, 50, 1),
                new ChapterTestScoreRow(2, 11L, 100, 1)));

    ChapterTestStatsDto dto = service.chapterTests(teacher, 9L);

    assertThat(dto.totalStudents()).isEqualTo(4);
    assertThat(dto.chapters()).hasSize(7);
    ChapterTestRow ch1 = dto.chapters().get(0);
    assertThat(ch1.participantCount()).isEqualTo(3);
    assertThat(ch1.attemptCount()).isEqualTo(4);
    assertThat(ch1.passedCount()).isEqualTo(2); // 90、70 ≥ 60；50 不及格
    assertThat(ch1.passRate()).isEqualTo(0.667);
    ScoreDistribution box = ch1.scoreDistribution();
    assertThat(box.count()).isEqualTo(3);
    assertThat(box.min()).isEqualTo(50);
    assertThat(box.q1()).isEqualTo(60);
    assertThat(box.median()).isEqualTo(70);
    assertThat(box.q3()).isEqualTo(80);
    assertThat(box.max()).isEqualTo(90);
    // 单样本章：五数全等于该分。
    ChapterTestRow ch2 = dto.chapters().get(1);
    assertThat(ch2.scoreDistribution().median()).isEqualTo(100);
    assertThat(ch2.passRate()).isEqualTo(1.0);
    // 无数据章：空分布。
    ChapterTestRow ch3 = dto.chapters().get(2);
    assertThat(ch3.participantCount()).isZero();
    assertThat(ch3.passRate()).isZero();
    assertThat(ch3.scoreDistribution().count()).isZero();
    assertThat(ch3.scoreDistribution().median()).isNull();
  }

  @Test
  void endlessActivityAggregatesSevenDayWindowFromRecoveryEvents() {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    when(repository.countActiveMembers(9L)).thenReturn(5);
    when(repository.findEndlessRecoveryEvents(eq(9L), eq(7)))
        .thenReturn(
            List.of(
                new EndlessRecoveryRow(today, 1, 11L),
                new EndlessRecoveryRow(today, 1, 12L),
                new EndlessRecoveryRow(today.minusDays(1), 2, 11L)));

    EndlessActivityDto dto = service.endlessActivity(teacher, 9L);

    assertThat(dto.totalStudents()).isEqualTo(5);
    assertThat(dto.periodEnd()).isEqualTo(today);
    assertThat(dto.periodStart()).isEqualTo(today.minusDays(6));
    assertThat(dto.activeStudentCount()).isEqualTo(2); // 去重 {11,12}
    assertThat(dto.recoveryRoundTotal()).isEqualTo(3);
    assertThat(dto.recoveryStarTotal()).isEqualTo(3);
    assertThat(dto.accuracyPct()).isNull(); // §3.2 无无尽轮次明细表
    assertThat(dto.daily()).hasSize(7);
    DailyEndless todayBucket =
        dto.daily().stream().filter(d -> d.day().equals(today)).findFirst().orElseThrow();
    assertThat(todayBucket.recoveryRounds()).isEqualTo(2);
    assertThat(todayBucket.recoveryStars()).isEqualTo(2);
    assertThat(dto.chapters()).hasSize(7);
    assertThat(dto.chapters().get(0).recoveryRounds()).isEqualTo(2);
    assertThat(dto.chapters().get(0).studentCount()).isEqualTo(2);
    assertThat(dto.chapters().get(1).recoveryRounds()).isEqualTo(1);
  }

  @Test
  void mainlineProgressBuildsMemberByChapterMatrixWithZeroProgressMember() {
    when(repository.findStageCountsPerChapter())
        .thenReturn(List.of(new StageCountRow(1, 3), new StageCountRow(2, 4)));
    when(repository.findStudentStarTotals(9L))
        .thenReturn(
            List.of(
                new StudentStarTotalRow(11L, "Stu A", 5, 2),
                new StudentStarTotalRow(12L, "Stu B", 0, 0)));
    when(repository.findMemberChapterProgress(9L))
        .thenReturn(
            List.of(
                new MemberChapterProgressRow(11L, "Stu A", 1, 2),
                new MemberChapterProgressRow(11L, "Stu A", 2, 1),
                new MemberChapterProgressRow(12L, "Stu B", null, 0)));

    MainlineProgressMatrixDto dto = service.mainlineProgress(teacher, 9L);

    assertThat(dto.totalStudents()).isEqualTo(2);
    assertThat(dto.totalStages()).isEqualTo(7);
    assertThat(dto.chapterStageCounts()).hasSize(7);
    assertThat(dto.chapterStageCounts().get(0).stageCount()).isEqualTo(3);
    assertThat(dto.chapterStageCounts().get(1).stageCount()).isEqualTo(4);
    assertThat(dto.chapterStageCounts().get(2).stageCount()).isZero();
    assertThat(dto.members()).hasSize(2);
    // 进度高者排前：Stu A（完成 3 个 stage）。
    MemberProgressRow first = dto.members().get(0);
    assertThat(first.studentId()).isEqualTo(11L);
    assertThat(first.completedStages()).isEqualTo(3);
    assertThat(first.totalStars()).isEqualTo(5);
    assertThat(first.chapters()).hasSize(7);
    assertThat(first.chapters().get(0).completedStages()).isEqualTo(2);
    assertThat(first.chapters().get(0).totalStages()).isEqualTo(3);
    assertThat(first.chapters().get(1).completedStages()).isEqualTo(1);
    assertThat(first.chapters().get(1).totalStages()).isEqualTo(4);
    // 零进度成员仍登记，全 0 单元格。
    MemberProgressRow second = dto.members().get(1);
    assertThat(second.studentId()).isEqualTo(12L);
    assertThat(second.completedStages()).isZero();
    assertThat(second.chapters().get(0).completedStages()).isZero();
  }

  @Test
  void aggregationCachedWithinTtlAndReloadedAfterExpiry() {
    when(repository.findStudentStarTotals(9L))
        .thenReturn(List.of(new StudentStarTotalRow(11L, "Stu A", 1, 1)));
    when(repository.findChapterStarBuckets(9L))
        .thenReturn(List.of(new ChapterStarBucketRow(1, 1, 1)));

    service.mainlineStars(teacher, 9L);
    service.mainlineStars(teacher, 9L); // TTL 内：命中缓存，不再查库
    verify(repository, times(1)).findStudentStarTotals(9L);

    now[0] += MainlineDashboardService.CACHE_TTL_MS + 1; // 越过 5 分钟 TTL
    service.mainlineStars(teacher, 9L);
    verify(repository, times(2)).findStudentStarTotals(9L);
  }

  @Test
  void removesExpiredAggregationEntriesDuringScheduledCacheCleanup() throws ReflectiveOperationException {
    when(repository.findStudentStarTotals(9L)).thenReturn(List.of());
    when(repository.findChapterStarBuckets(9L)).thenReturn(List.of());
    service.mainlineStars(teacher, 9L);
    now[0] += MainlineDashboardService.CACHE_TTL_MS;

    Method cleanup = MainlineDashboardService.class.getDeclaredMethod("cleanupExpiredCache");
    cleanup.setAccessible(true);
    cleanup.invoke(service);

    Field cacheField = MainlineDashboardService.class.getDeclaredField("cache");
    cacheField.setAccessible(true);
    assertThat((Map<?, ?>) cacheField.get(service)).isEmpty();
  }

  @Test
  void boxplotComputesFiveNumberSummary() {
    ScoreDistribution box =
        MainlineDashboardService.boxplot(List.of(60, 70, 80, 90, 100));
    assertThat(box.count()).isEqualTo(5);
    assertThat(box.min()).isEqualTo(60);
    assertThat(box.q1()).isEqualTo(70);
    assertThat(box.median()).isEqualTo(80);
    assertThat(box.q3()).isEqualTo(90);
    assertThat(box.max()).isEqualTo(100);
  }

  @Test
  void boxplotEmptyReturnsNulls() {
    ScoreDistribution box = MainlineDashboardService.boxplot(List.of());
    assertThat(box.count()).isZero();
    assertThat(box.min()).isNull();
    assertThat(box.median()).isNull();
    assertThat(box.max()).isNull();
  }

  @Test
  void percentileInterpolatesBetweenRanks() {
    int[] sorted = {50, 70, 90};
    assertThat(MainlineDashboardService.percentile(sorted, 0.25)).isEqualTo(60);
    assertThat(MainlineDashboardService.percentile(sorted, 0.50)).isEqualTo(70);
    assertThat(MainlineDashboardService.percentile(sorted, 0.75)).isEqualTo(80);
  }
}
