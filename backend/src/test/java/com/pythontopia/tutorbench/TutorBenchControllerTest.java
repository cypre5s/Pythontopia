package com.pythontopia.tutorbench;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.StudentWriteThrottle;
import org.junit.jupiter.api.Test;

class TutorBenchControllerTest {

  @Test
  void limitsExpensiveBenchmarkRunsPerStudentAccount() {
    TutorBenchService service = mock(TutorBenchService.class);
    TutorBenchController controller =
        new TutorBenchController(service, new StudentWriteThrottle());
    CurrentUserPrincipal student = new CurrentUserPrincipal(42L, "student", Role.STUDENT);

    for (int run = 0; run < 10; run++) {
      controller.run(student);
    }

    assertThatThrownBy(() -> controller.run(student))
        .isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).code())
        .isEqualTo("TUTOR_BENCH_RATE_LIMITED");
    verify(service, times(10)).run(42L);
  }
}
