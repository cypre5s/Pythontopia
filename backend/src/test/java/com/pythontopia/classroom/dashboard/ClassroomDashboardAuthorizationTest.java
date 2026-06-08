package com.pythontopia.classroom.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.classroom.dashboard.repository.ClassroomDashboardRepository;
import com.pythontopia.classroom.dashboard.service.ClassroomDashboardService;
import com.pythontopia.classroom.repository.ClassroomRepository;
import com.pythontopia.classroom.security.ClassroomGuard;
import com.pythontopia.common.error.BusinessException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * 红线测试：班级聚合数据（KC 热力图 / 高频误区 / 任务完成率 / 资源使用 / 最近活动）仅教师可读，
 * 学生（非教师角色）一律被拒（{@code FORBIDDEN}）。
 *
 * <p>这里用「真实」的 {@link ClassroomGuard} 串起 {@link ClassroomDashboardService}（控制器
 * {@code @PreAuthorize} 之外的服务层独立防线），确保即便绕过控制器，学生也读不到任何班级聚合；
 * 并断言学生请求在角色校验阶段即被拦下、绝不触达聚合仓储（不泄露任何聚合数据）。
 */
class ClassroomDashboardAuthorizationTest {

  private final ClassroomDashboardRepository dashboardRepository =
      mock(ClassroomDashboardRepository.class);
  // requireOwner 在角色校验阶段即拒绝学生，不会触达 classrooms 表，故该仓储可为纯 mock。
  private final ClassroomRepository classroomRepository = mock(ClassroomRepository.class);
  private final ClassroomGuard classroomGuard = new ClassroomGuard(classroomRepository);
  private final ClassroomDashboardService service =
      new ClassroomDashboardService(dashboardRepository, classroomGuard);

  private final CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "stu01", Role.STUDENT);

  @Test
  void studentCannotReadKcHeatmap() {
    assertForbidden(() -> service.kcHeatmap(student, 9L));
  }

  @Test
  void studentCannotReadQuestSummary() {
    assertForbidden(() -> service.questSummary(student, 9L));
  }

  @Test
  void studentCannotReadErrorHotspots() {
    assertForbidden(() -> service.errorHotspots(student, 9L));
  }

  @Test
  void studentCannotReadResourceUsage() {
    assertForbidden(() -> service.resourceUsage(student, 9L));
  }

  @Test
  void studentCannotReadRecentActivity() {
    assertForbidden(() -> service.recentActivity(student, 9L, 20));
  }

  @Test
  void studentCannotReadCopilotBrief() {
    // 教师 copilot 的班级学情摘要走同一道归属闸：学生答疑即便携带 classroomId 也读不到任何聚合。
    assertForbidden(() -> service.copilotBrief(student, 9L));
  }

  private void assertForbidden(ThrowingCallable call) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.status()).isEqualTo(HttpStatus.FORBIDDEN));
    // 红线：学生被拒后绝不能读到任何班级聚合 → 聚合仓储零交互。
    verifyNoInteractions(dashboardRepository);
  }
}
