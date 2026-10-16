package com.pythontopia.tutor;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.tutor.dto.StyleEngagedRequest;
import org.junit.jupiter.api.Test;

class TutorStyleEngagedThrottleTest {

  private static final long USER_ID = 7L;

  private final TutorService service = mock(TutorService.class);
  private final StudentWriteThrottle throttle = mock(StudentWriteThrottle.class);
  private final TutorController controller = new TutorController(service, throttle);
  private final CurrentUserPrincipal student =
      new CurrentUserPrincipal(USER_ID, "student", Role.STUDENT);

  @Test
  void rejectsOverBudgetProfilePreferenceBeforePublishingEvent() {
    when(throttle.tryAcquire(USER_ID, 1)).thenReturn(false);

    assertThatThrownBy(
            () -> controller.styleEngaged(student, new StyleEngagedRequest("visual", null)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> {
              assertThat(error.code()).isEqualTo("STUDENT_WRITE_RATE_LIMITED");
              assertThat(error.status().value()).isEqualTo(429);
            });

    verify(service, never()).recordStyleEngaged(USER_ID, "visual", null);
  }

  @Test
  void onlyStudentPreferenceWritesConsumeTheSharedBudget() {
    when(throttle.tryAcquire(USER_ID, 1)).thenReturn(true);

    controller.styleEngaged(student, new StyleEngagedRequest("logical", "PY43-KC-001"));

    verify(throttle).tryAcquire(USER_ID, 1);
    verify(service).recordStyleEngaged(USER_ID, "logical", "PY43-KC-001");
  }

  @Test
  void rejectsTeacherBeforeReservingStudentBudget() {
    CurrentUserPrincipal teacher = new CurrentUserPrincipal(9L, "teacher", Role.TEACHER);

    assertThatThrownBy(
            () -> controller.styleEngaged(teacher, new StyleEngagedRequest("trial", null)))
        .isInstanceOf(BusinessException.class);

    verifyNoInteractions(throttle, service);
  }
}
