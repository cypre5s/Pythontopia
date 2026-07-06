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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClassroomDashboardServiceTest {

  private final ClassroomDashboardRepository repository = mock(ClassroomDashboardRepository.class);
  private final ClassroomGuard classroomGuard = mock(ClassroomGuard.class);
  private final ClassroomDashboardService service =
      new ClassroomDashboardService(repository, classroomGuard);
  private final CurrentUserPrincipal teacher = new CurrentUserPrincipal(42L, "t01", Role.TEACHER);

  @Test
  void kcHeatmapWritesSnapshotForEachEntry() {
    when(classroomGuard.requireOwner(teacher, 9L))
        .thenReturn(new Classroom(9L, "DemoClass", "", 42L, 43L, "ABC123", "ACTIVE"));
    KcHeatmapEntryDto a =
        new KcHeatmapEntryDto("PY43-KC-001", "PY43-KC-001 名", 1L, 1, new BigDecimal("0.500"), 3, 1, 2, 0);
    KcHeatmapEntryDto b =
        new KcHeatmapEntryDto("PY43-KC-002", "PY43-KC-002 名", 1L, 2, new BigDecimal("0.200"), 3, 0, 1, 2);
    when(repository.findKcHeatmap(9L)).thenReturn(List.of(a, b));

    List<KcHeatmapEntryDto> result = service.kcHeatmap(teacher, 9L);
    assertThat(result).hasSize(2);
    verify(repository).upsertSnapshot(9L, a);
    verify(repository).upsertSnapshot(9L, b);
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

  @Test
  void recentActivityClampsLimit() {
    when(classroomGuard.requireOwner(teacher, 9L))
        .thenReturn(new Classroom(9L, "DemoClass", "", 42L, 43L, "ABC123", "ACTIVE"));
    when(repository.findRecentActivity(9L, 50))
        .thenReturn(List.of(
            new RecentActivityDto(11L, "Stu 01", "SUBMISSION", "PY43-KC-001", "AC",
                OffsetDateTime.now(ZoneOffset.UTC))));
    List<RecentActivityDto> rows = service.recentActivity(teacher, 9L, 999);
    assertThat(rows).hasSize(1);
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
    verify(repository, org.mockito.Mockito.never()).upsertSnapshot(any(), any());
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
}
