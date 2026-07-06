package com.pythontopia.tutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.classroom.dashboard.service.ClassroomDashboardService;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

/**
 * 教师 copilot 数据级注入的红线与降级单测：
 *
 * <ul>
 *   <li>学生角色携带 {@code classroomId} 时，{@link TutorService#resolveClassroomBrief} 一律早退返回空串，
 *       且<b>绝不调用</b> {@link ClassroomDashboardService}（学生零触达班级聚合）。
 *   <li>归属教师才注入本班学情；取数异常 / 非归属（抛 FORBIDDEN）一律降级为空串，不破坏答疑。
 *   <li>{@link TutorService#mergeContext} 把摘要前置到原始上下文。
 * </ul>
 */
class TutorClassroomBriefTest {

  private final ClassroomDashboardService dashboard = mock(ClassroomDashboardService.class);
  private final TutorService service =
      new TutorService(
          mock(TutorGraphClient.class),
          mock(TutorSessionRepository.class),
          new ObjectMapper(),
          mock(PassiveProfileService.class),
          mock(ApplicationEventPublisher.class),
          dashboard,
          mock(com.pythontopia.learner.memory.MemoryRetrievalService.class));

  private static CurrentUserPrincipal student() {
    return new CurrentUserPrincipal(7L, "stu01", Role.STUDENT);
  }

  private static CurrentUserPrincipal teacher() {
    return new CurrentUserPrincipal(42L, "tea01", Role.TEACHER);
  }

  @Test
  void studentClassroomIdIsIgnoredAndNeverReachesDashboard() {
    String brief = service.resolveClassroomBrief(student(), 9L);
    assertThat(brief).isEmpty();
    // 红线：学生答疑绝不触达班级聚合数据源。
    verifyNoInteractions(dashboard);
  }

  @Test
  void teacherOwnerGetsClassBrief() {
    when(dashboard.copilotBrief(teacher(), 9L)).thenReturn("【本班只读学情】最弱知识点：列表推导式");
    String brief = service.resolveClassroomBrief(teacher(), 9L);
    assertThat(brief).contains("列表推导式");
  }

  @Test
  void nullClassroomIdReturnsEmptyWithoutDashboard() {
    assertThat(service.resolveClassroomBrief(teacher(), null)).isEmpty();
    verifyNoInteractions(dashboard);
  }

  @Test
  void nonOwnerOrFailureDegradesToEmpty() {
    when(dashboard.copilotBrief(teacher(), 9L))
        .thenThrow(new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "班级不属于当前教师"));
    assertThat(service.resolveClassroomBrief(teacher(), 9L)).isEmpty();
  }

  @Test
  void mergeContextPrependsBriefBeforeOriginalContext() {
    assertThat(TutorService.mergeContext("班级摘要", "原始上下文"))
        .isEqualTo("班级摘要\n\n原始上下文");
    assertThat(TutorService.mergeContext("班级摘要", null)).isEqualTo("班级摘要");
    assertThat(TutorService.mergeContext("", "原始上下文")).isEqualTo("原始上下文");
    assertThat(TutorService.mergeContext("  ", "原始上下文")).isEqualTo("原始上下文");
  }
}
