package com.pythontopia.classroom.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.classroom.dashboard.repository.MainlineDashboardRepository;
import com.pythontopia.classroom.dashboard.service.MainlineDashboardService;
import com.pythontopia.classroom.repository.ClassroomRepository;
import com.pythontopia.classroom.security.ClassroomGuard;
import com.pythontopia.common.error.BusinessException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * 红线测试：R-15 主线看板 4 个聚合端点（星级矩阵 / 章测 / 无尽活跃 / 进度矩阵）仅教师可读，学生一律
 * {@code FORBIDDEN}。用「真实」{@link ClassroomGuard} 串起 {@link MainlineDashboardService}（控制器
 * {@code @PreAuthorize} 之外的服务层独立防线），并断言学生请求在角色校验阶段即被拦下、绝不触达聚合仓储。
 */
class MainlineDashboardAuthorizationTest {

  private final MainlineDashboardRepository repository = mock(MainlineDashboardRepository.class);
  // requireOwner 在角色校验阶段即拒绝学生，不会触达 classrooms 表，故该仓储可为纯 mock。
  private final ClassroomRepository classroomRepository = mock(ClassroomRepository.class);
  private final ClassroomGuard guard = new ClassroomGuard(classroomRepository);
  private final MainlineDashboardService service = new MainlineDashboardService(repository, guard);

  private final CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "stu01", Role.STUDENT);

  @Test
  void studentCannotReadMainlineStars() {
    assertForbidden(() -> service.mainlineStars(student, 9L));
  }

  @Test
  void studentCannotReadChapterTests() {
    assertForbidden(() -> service.chapterTests(student, 9L));
  }

  @Test
  void studentCannotReadEndlessActivity() {
    assertForbidden(() -> service.endlessActivity(student, 9L));
  }

  @Test
  void studentCannotReadMainlineProgress() {
    assertForbidden(() -> service.mainlineProgress(student, 9L));
  }

  private void assertForbidden(ThrowingCallable call) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.status()).isEqualTo(HttpStatus.FORBIDDEN));
    // 红线：学生被拒后绝不能读到任何主线聚合 → 聚合仓储零交互。
    verifyNoInteractions(repository);
  }
}
