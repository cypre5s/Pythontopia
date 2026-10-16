package com.pythontopia.learner.evidence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.StudentWriteThrottle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class EvidenceMemoryControllerTest {

  @Mock private EvidenceMemoryService service;

  @Mock private StudentWriteThrottle studentWriteThrottle;

  @InjectMocks private EvidenceMemoryController controller;

  @Test
  void exhaustedAccountWriteCapacityPreventsSnapshotBuildAndPersistence() {
    CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "student", Role.STUDENT);
    when(studentWriteThrottle.tryAcquire(7L, 1)).thenReturn(false);

    assertThatThrownBy(() -> controller.snapshot(student, null, 160))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> {
              org.assertj.core.api.Assertions.assertThat(error.status())
                  .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
              org.assertj.core.api.Assertions.assertThat(error.code())
                  .isEqualTo("STUDENT_WRITE_RATE_LIMITED");
            });

    verify(studentWriteThrottle).tryAcquire(7L, 1);
    verify(service, never()).createSnapshot(7L, null, 160, true);
  }
}
