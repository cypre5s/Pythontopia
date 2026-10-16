package com.pythontopia.classroom.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.classroom.dashboard.dto.ErrorHotspotDto;
import com.pythontopia.classroom.dashboard.dto.KcHeatmapEntryDto;
import com.pythontopia.classroom.dashboard.dto.QuestCompletionDto;
import com.pythontopia.classroom.dashboard.dto.RecentActivityDto;
import com.pythontopia.classroom.dashboard.dto.ResourceUsageDto;
import com.pythontopia.classroom.dashboard.repository.ClassroomDashboardRepository;
import com.pythontopia.classroom.dashboard.service.ClassroomDashboardService;
import com.pythontopia.classroom.domain.Classroom;
import com.pythontopia.classroom.security.ClassroomGuard;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.StudentWriteThrottle;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ClassroomDashboardServiceTest {

  private final ClassroomDashboardRepository repository = mock(ClassroomDashboardRepository.class);
  private final ClassroomGuard classroomGuard = mock(ClassroomGuard.class);
  private final StudentWriteThrottle studentWriteThrottle = mock(StudentWriteThrottle.class);
  private final ClassroomDashboardService service =
      new ClassroomDashboardService(repository, classroomGuard, studentWriteThrottle);
  private final CurrentUserPrincipal teacher = new CurrentUserPrincipal(42L, "t01", Role.TEACHER);

  /**
   * 回归（缺陷 9a）：快照刷新必须是<b>一次</b>批量 upsert。旧实现对 65 个 KC 逐条
   * {@code INSERT ON CONFLICT}，而班级详情页 onMounted / 路由切换 / 发布任务后各调一次热力图，
   * 等于每次打开班级就打 65 条往返。
   */
  @Test
  void kcHeatmapWritesAllSnapshotsInOneBatch() {
    when(classroomGuard.requireOwner(teacher, 9L))
        .thenReturn(new Classroom(9L, "DemoClass", "", 42L, 43L, "ABC123", "ACTIVE"));
    when(studentWriteThrottle.tryAcquireTeacherKcHeatmap(42L)).thenReturn(true);
    KcHeatmapEntryDto a =
        new KcHeatmapEntryDto("PY43-KC-001", "PY43-KC-001 名", 1L, 1, new BigDecimal("0.500"), 3, 1, 2, 0);
    KcHeatmapEntryDto b =
        new KcHeatmapEntryDto("PY43-KC-002", "PY43-KC-002 名", 1L, 2, new BigDecimal("0.200"), 3, 0, 1, 2);
    when(repository.findKcHeatmap(9L)).thenReturn(List.of(a, b));

    List<KcHeatmapEntryDto> result = service.kcHeatmap(teacher, 9L);
    assertThat(result).hasSize(2);
    verify(studentWriteThrottle).tryAcquireTeacherKcHeatmap(42L);
    verify(repository, org.mockito.Mockito.times(1)).upsertSnapshots(9L, List.of(a, b));
  }

  @Test
  void kcHeatmapRejectsOverBudgetBeforeAggregationOrSnapshotWrite() {
    when(classroomGuard.requireOwner(teacher, 9L))
        .thenReturn(new Classroom(9L, "DemoClass", "", 42L, 43L, "ABC123", "ACTIVE"));
    when(studentWriteThrottle.tryAcquireTeacherKcHeatmap(42L)).thenReturn(false);

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.kcHeatmap(teacher, 9L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));

    verify(studentWriteThrottle).tryAcquireTeacherKcHeatmap(42L);
    org.mockito.Mockito.verifyNoInteractions(repository);
  }

  @Test
  void eleventhHeatmapRefreshIsRejectedBeforeAggregationOrSnapshotWrite() {
    when(classroomGuard.requireOwner(teacher, 9L))
        .thenReturn(new Classroom(9L, "DemoClass", "", 42L, 43L, "ABC123", "ACTIVE"));
    when(repository.findKcHeatmap(9L)).thenReturn(List.of());
    StudentWriteThrottle throttle = new StudentWriteThrottle();
    ClassroomDashboardService rateLimitedService =
        new ClassroomDashboardService(repository, classroomGuard, throttle);

    for (int request = 0; request < 10; request++) {
      assertThat(rateLimitedService.kcHeatmap(teacher, 9L)).isEmpty();
    }
    assertThat(throttle.tryAcquire(42L, 100)).isTrue();

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> rateLimitedService.kcHeatmap(teacher, 9L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));

    verify(repository, org.mockito.Mockito.times(10)).findKcHeatmap(9L);
    verify(repository, org.mockito.Mockito.times(10)).upsertSnapshots(9L, List.of());
  }

  @Test
  void questSummaryDelegates() {
    when(classroomGuard.requireOwner(teacher, 9L))
        .thenReturn(new Classroom(9L, "DemoClass", "", 42L, 43L, "ABC123", "ACTIVE"));
    QuestCompletionDto entry =
        new QuestCompletionDto(
            77L, "for 循环", LocalDate.now(ZoneOffset.UTC).plusDays(2), "ACTIVE",
            3, 2, 0, 0, new BigDecimal("0.667"));
    when(repository.findQuestSummary(9L)).thenReturn(List.of(entry));
    List<QuestCompletionDto> rows = service.questSummary(teacher, 9L);
    assertThat(rows).containsExactly(entry);
  }

  @Test
  void errorHotspotsUsesSevenDayWindow() {
    when(classroomGuard.requireOwner(teacher, 9L))
        .thenReturn(new Classroom(9L, "DemoClass", "", 42L, 43L, "ABC123", "ACTIVE"));
    when(repository.findErrorHotspots(eq(9L), any(Integer.class)))
        .thenReturn(List.of(new ErrorHotspotDto("PY43-KC-001", "kc1", 5, 3, List.of())));
    List<ErrorHotspotDto> rows = service.errorHotspots(teacher, 9L);
    assertThat(rows).hasSize(1);
  }

  @Test
  void resourceUsageDelegates() {
    when(classroomGuard.requireOwner(teacher, 9L))
        .thenReturn(new Classroom(9L, "DemoClass", "", 42L, 43L, "ABC123", "ACTIVE"));
    when(repository.findResourceUsage(eq(9L), any(Integer.class)))
        .thenReturn(new ResourceUsageDto(LocalDate.now(ZoneOffset.UTC), LocalDate.now(ZoneOffset.UTC), List.of(), List.of()));
    assertThat(service.resourceUsage(teacher, 9L)).isNotNull();
  }

  /**
   * 回归（缺陷 9b）：最近活跃事件流必须带 7 天窗口（与高频误区 / 资源使用同口径），
   * 且 limit 仍被夹到 50。旧实现三路 UNION ALL 无任何时间下界，LIMIT 只在全量排序之后生效。
   */
  @Test
  void recentActivityClampsLimitAndUsesSevenDayWindow() {
    when(classroomGuard.requireOwner(teacher, 9L))
        .thenReturn(new Classroom(9L, "DemoClass", "", 42L, 43L, "ABC123", "ACTIVE"));
    when(repository.findRecentActivity(9L, 50, 7))
        .thenReturn(List.of(
            new RecentActivityDto(11L, "Stu 01", "SUBMISSION", "PY43-KC-001", "AC",
                OffsetDateTime.now(ZoneOffset.UTC))));
    List<RecentActivityDto> rows = service.recentActivity(teacher, 9L, 999);
    assertThat(rows).hasSize(1);
    verify(repository).findRecentActivity(9L, 50, 7);
  }

  @Test
  void copilotBriefRendersWeakestKcsAndErrorsForOwner() {
    when(classroomGuard.requireOwner(teacher, 9L))
        .thenReturn(new Classroom(9L, "DemoClass", "", 42L, 43L, "ABC123", "ACTIVE"));
    KcHeatmapEntryDto strong =
        new KcHeatmapEntryDto("PY43-KC-001", "变量与赋值", 1L, 1, new BigDecimal("0.910"), 5, 5, 0, 0);
    KcHeatmapEntryDto weak =
        new KcHeatmapEntryDto("PY43-KC-016", "列表推导式", 2L, 5, new BigDecimal("0.220"), 5, 0, 1, 4);
    when(repository.findKcHeatmap(9L)).thenReturn(List.of(strong, weak));
    when(repository.findErrorHotspots(eq(9L), any(Integer.class)))
        .thenReturn(List.of(new ErrorHotspotDto("PY43-KC-016", "列表推导式", 12, 5, List.of())));

    String brief = service.copilotBrief(teacher, 9L);

    assertThat(brief).contains("列表推导式").contains("4人挣扎").contains("12次失败");
    // 最弱知识点应优先列出挣扎人数最高的 KC，而非掌握度高的 KC。
    assertThat(brief).contains("最弱知识点").contains("高频错误");
    // 红线：摘要必须自带「只读 + 不泄题」声明。
    assertThat(brief).contains("泄题");
    // 只读：copilotBrief 绝不写快照。
    verify(repository, org.mockito.Mockito.never()).upsertSnapshots(any(), any());
  }

  @Test
  void copilotBriefReturnsPlaceholderWhenNoData() {
    when(classroomGuard.requireOwner(teacher, 9L))
        .thenReturn(new Classroom(9L, "DemoClass", "", 42L, 43L, "ABC123", "ACTIVE"));
    when(repository.findKcHeatmap(9L)).thenReturn(List.of());
    when(repository.findErrorHotspots(eq(9L), any(Integer.class))).thenReturn(List.of());

    String brief = service.copilotBrief(teacher, 9L);
    assertThat(brief).contains("暂无足够学情数据").contains("泄题");
  }

  /**
   * 回归（缺陷 6）：刚建好的班（有成员但一个 KC 都没练过）不得输出「最弱知识点：xxx(掌握度0.00)」。
   *
   * <p>旧过滤条件是 {@code studentCount() > 0}，而 studentCount 是<b>班级规模</b>而非「练过这个 KC
   * 的人数」——只要班里有 1 个人，65 个 KC 全部通过过滤，「暂无足够学情数据」分支变成死代码，
   * copilot 会把从没教过的 KC 断言成本班最弱知识点。
   */
  @Test
  void copilotBriefTreatsUntouchedKcsAsNoData() {
    when(classroomGuard.requireOwner(teacher, 9L))
        .thenReturn(new Classroom(9L, "DemoClass", "", 42L, 43L, "ABC123", "ACTIVE"));
    // 30 人班，两个 KC 都还没人练过：student_count=30，但三个分桶全为 0。
    when(repository.findKcHeatmap(9L))
        .thenReturn(
            List.of(
                new KcHeatmapEntryDto(
                    "PY43-KC-050", "还没教的 KC", 7L, 50, BigDecimal.ZERO, 30, 0, 0, 0),
                new KcHeatmapEntryDto(
                    "PY43-KC-051", "更靠后的 KC", 7L, 51, BigDecimal.ZERO, 30, 0, 0, 0)));
    when(repository.findErrorHotspots(eq(9L), any(Integer.class))).thenReturn(List.of());

    String brief = service.copilotBrief(teacher, 9L);

    assertThat(brief).contains("暂无足够学情数据");
    assertThat(brief).doesNotContain("最弱知识点");
    assertThat(brief).doesNotContain("还没教的 KC");
  }

  /** 有人练过（哪怕只有 1 人）才进入最弱知识点列表。 */
  @Test
  void copilotBriefListsKcOnceAtLeastOneStudentPracticed() {
    when(classroomGuard.requireOwner(teacher, 9L))
        .thenReturn(new Classroom(9L, "DemoClass", "", 42L, 43L, "ABC123", "ACTIVE"));
    when(repository.findKcHeatmap(9L))
        .thenReturn(
            List.of(
                new KcHeatmapEntryDto(
                    "PY43-KC-050", "还没教的 KC", 7L, 50, BigDecimal.ZERO, 30, 0, 0, 0),
                new KcHeatmapEntryDto(
                    "PY43-KC-016", "列表推导式", 2L, 16, new BigDecimal("0.030"), 30, 0, 0, 1)));
    when(repository.findErrorHotspots(eq(9L), any(Integer.class))).thenReturn(List.of());

    String brief = service.copilotBrief(teacher, 9L);

    assertThat(brief).contains("最弱知识点").contains("列表推导式");
    assertThat(brief).doesNotContain("还没教的 KC");
  }
}
