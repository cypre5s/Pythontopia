package com.pythontopia.world.curriculum;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.StudentWriteThrottle;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;

class CurriculumControllerSecurityTest {

  private final CurriculumMapperService service = mock(CurriculumMapperService.class);
  private final StudentWriteThrottle studentWriteThrottle = mock(StudentWriteThrottle.class);
  private final CurriculumController controller =
      new CurriculumController(service, studentWriteThrottle);
  private final CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "student", Role.STUDENT);

  @Test
  void rejectsNonStudentBeforeRunningBootstrap() {
    CurrentUserPrincipal teacher = new CurrentUserPrincipal(8L, "teacher", Role.TEACHER);

    assertThatThrownBy(() -> controller.bootstrap(teacher))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> {
              org.assertj.core.api.Assertions.assertThat(error.status())
                  .isEqualTo(HttpStatus.FORBIDDEN);
              org.assertj.core.api.Assertions.assertThat(error.code()).isEqualTo("FORBIDDEN");
            });

    verifyNoInteractions(service, studentWriteThrottle);
  }

  @Test
  void reservesAccountWriteCapacityBeforeRunningBootstrap() {
    when(studentWriteThrottle.tryAcquire(7L, 1)).thenReturn(true);

    controller.bootstrap(student);

    InOrder order = inOrder(studentWriteThrottle, service);
    order.verify(studentWriteThrottle).tryAcquire(7L, 1);
    order.verify(service).bootstrap(7L);
  }

  @Test
  void exhaustedAccountWriteCapacityPreventsBootstrap() {
    when(studentWriteThrottle.tryAcquire(7L, 1)).thenReturn(false);

    assertThatThrownBy(() -> controller.bootstrap(student))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> {
              org.assertj.core.api.Assertions.assertThat(error.status())
                  .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
              org.assertj.core.api.Assertions.assertThat(error.code())
                  .isEqualTo("STUDENT_WRITE_RATE_LIMITED");
            });

    verify(service, never()).bootstrap(7L);
  }
}
