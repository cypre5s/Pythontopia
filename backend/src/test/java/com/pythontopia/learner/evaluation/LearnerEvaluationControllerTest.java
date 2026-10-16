package com.pythontopia.learner.evaluation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.learner.evaluation.controller.LearnerEvaluationController;
import com.pythontopia.learner.evaluation.service.LearnerEvaluationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class LearnerEvaluationControllerTest {

  private static final CurrentUserPrincipal STUDENT =
      new CurrentUserPrincipal(7L, "student", Role.STUDENT);

  @Test
  void pathDecisionsShareThePerAccountWriteBudget() {
    var service = mock(LearnerEvaluationService.class);
    var controller = new LearnerEvaluationController(service, new StudentWriteThrottle());

    for (int write = 0; write < 100; write++) {
      controller.accept(STUDENT, (long) write + 1);
    }

    assertThatThrownBy(() -> controller.keepOld(STUDENT, 101L))
        .isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).status())
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    verify(service, times(100)).acceptNewPath(eq(7L), anyLong());
  }
}
