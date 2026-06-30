package com.pythontopia.workshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.workshop.controller.ResourceWorkshopController;
import com.pythontopia.workshop.dto.ResourceEffectivenessDto;
import com.pythontopia.workshop.dto.ResourceFeedbackStatus;
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
  private final ResourceWorkshopController controller = new ResourceWorkshopController(service);

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
}
