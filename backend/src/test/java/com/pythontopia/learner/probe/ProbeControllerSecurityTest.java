package com.pythontopia.learner.probe;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.learner.probe.dto.ProbeRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ProbeControllerSecurityTest {

  @Mock private ApplicationEventPublisher eventPublisher;

  @Mock private StudentWriteThrottle studentWriteThrottle;

  @InjectMocks private ProbeController controller;

  @Test
  void exhaustedAccountWriteCapacityPreventsProbeEventPublication() {
    CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "student", Role.STUDENT);
    when(studentWriteThrottle.tryAcquire(7L, 1)).thenReturn(false);

    assertThatThrownBy(() -> controller.submit(student, new ProbeRequest("cog_intro", "visual")))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> {
              org.assertj.core.api.Assertions.assertThat(error.status())
                  .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
              org.assertj.core.api.Assertions.assertThat(error.code())
                  .isEqualTo("STUDENT_WRITE_RATE_LIMITED");
            });

    verify(studentWriteThrottle).tryAcquire(7L, 1);
    verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
  }
}
