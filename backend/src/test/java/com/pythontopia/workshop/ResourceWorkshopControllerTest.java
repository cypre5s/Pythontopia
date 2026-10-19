package com.pythontopia.workshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.workshop.controller.ResourceWorkshopController;
import com.pythontopia.workshop.dto.ResourceCheckEvidenceType;
import com.pythontopia.workshop.dto.ResourceEffectivenessDto;
import com.pythontopia.workshop.dto.ResourceFeedbackRequest;
import com.pythontopia.workshop.dto.ResourceFeedbackStatus;
import com.pythontopia.workshop.dto.ResourceCheckSubmitRequest;
import com.pythontopia.workshop.service.ResourceWorkshopService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ResourceWorkshopControllerTest {

  private static final CurrentUserPrincipal STUDENT =
      new CurrentUserPrincipal(7L, "stu", Role.STUDENT);
  private static final CurrentUserPrincipal TEACHER =
      new CurrentUserPrincipal(2L, "teacher", Role.TEACHER);

  private final ResourceWorkshopService service = mock(ResourceWorkshopService.class);
  private final ResourceWorkshopController controller =
      new ResourceWorkshopController(service, new StudentWriteThrottle());

  @Test
  void effectivenessDelegatesToServiceWithCurrentStudent() {
    ResourceEffectivenessDto dto =
        new ResourceEffectivenessDto(
            9L,
            ResourceFeedbackStatus.LIKED,
            1,
            0,
            2,
            2,
            0,
            new BigDecimal("1.000"),
            new BigDecimal("0.10"),
            List.of(101L),
            "EFFECTIVE",
            "resource_feedback_or_check");
    when(service.effectiveness(7L, 9L)).thenReturn(dto);

    ResourceEffectivenessDto result = controller.effectiveness(STUDENT, 9L);

    assertThat(result.signal()).isEqualTo("EFFECTIVE");
    verify(service).effectiveness(7L, 9L);
  }

  @Test
  void effectivenessRejectsNonStudent() {
    assertThatThrownBy(() -> controller.effectiveness(TEACHER, 9L))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).status())
        .isEqualTo(HttpStatus.FORBIDDEN);
    verifyNoInteractions(service);
  }

  @Test
  void forwardsOptionalIdempotencyKeyToResourceCheckService() {
    var request =
        new ResourceCheckSubmitRequest(
            "PY43-KC-001", ResourceCheckEvidenceType.MICRO_QUIZ, "x", null);

    controller.submitCheck(STUDENT, 9L, "micro-check-1", request);

    verify(service).submitCheck(7L, 9L, request, "micro-check-1");
  }

  @Test
  void limitsRepeatedFeedbackWritesPerAccount() {
    var request = new ResourceFeedbackRequest(ResourceFeedbackStatus.LIKED, null);

    for (int write = 0; write < 100; write++) {
      controller.feedback(STUDENT, 9L, request);
    }

    assertThatThrownBy(() -> controller.feedback(STUDENT, 9L, request))
        .isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).status())
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
  }

  @Test
  void limitsControllerManagedLearningWriteRoutesWhenAccountBudgetIsExhausted() {
    StudentWriteThrottle throttle = mock(StudentWriteThrottle.class);
    when(throttle.tryAcquire(7L, 1)).thenReturn(false);
    ResourceWorkshopController limitedController =
        new ResourceWorkshopController(service, throttle);
    var check =
        new ResourceCheckSubmitRequest("PY43-KC-001", ResourceCheckEvidenceType.MICRO_QUIZ, "x", null);
    var feedback = new ResourceFeedbackRequest(ResourceFeedbackStatus.LIKED, null);

    assertWriteRejected(() -> limitedController.consume(STUDENT, 9L));
    limitedController.submitCheck(STUDENT, 9L, null, check);
    assertWriteRejected(() -> limitedController.feedback(STUDENT, 9L, feedback));

    verify(throttle, times(2)).tryAcquire(7L, 1);
    verify(service).submitCheck(7L, 9L, check, null);
  }

  private void assertWriteRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable write) {
    assertThatThrownBy(write)
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).status())
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
  }
}
