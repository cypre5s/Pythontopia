package com.pythontopia.submission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.submission.controller.SubmissionController;
import com.pythontopia.submission.dto.SubmissionPerformanceDto;
import com.pythontopia.submission.service.SubmissionService;
import org.junit.jupiter.api.Test;

class SubmissionControllerTest {

  private final SubmissionService service = mock(SubmissionService.class);
  private final SubmissionController controller = new SubmissionController(service);

  @Test
  void performanceEndpointIsAvailableToStudentsAndPassesOwnerIdToService() {
    CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "student", Role.STUDENT);
    SubmissionPerformanceDto expected = new SubmissionPerformanceDto(80.0, 12, null, null);
    when(service.performance(7L, 42L)).thenReturn(expected);

    assertThat(controller.performance(student, 42L)).isEqualTo(expected);

    verify(service).performance(7L, 42L);
  }

  @Test
  void performanceEndpointRejectsNonStudents() {
    CurrentUserPrincipal teacher = new CurrentUserPrincipal(2L, "teacher", Role.TEACHER);

    assertThatThrownBy(() -> controller.performance(teacher, 42L))
        .isInstanceOf(BusinessException.class);
  }
}
