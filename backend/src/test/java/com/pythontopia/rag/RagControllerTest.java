package com.pythontopia.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.rag.dto.RagQueryRequestDto;
import com.pythontopia.rag.dto.RagQueryResponseDto;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RagControllerTest {

  @Test
  void queryForwardsFullRequestWithoutDroppingGraphOptions() {
    RagFacade facade = mock(RagFacade.class);
    StudentWriteThrottle throttle = mock(StudentWriteThrottle.class);
    RagController controller = new RagController(facade, throttle);
    RagQueryRequestDto request =
        new RagQueryRequestDto(
            "为什么字典应用需要字典基本操作",
            6,
            List.of("PY43-KC-041"),
            List.of(5L),
            0.2,
            "force",
            2,
            true);
    RagQueryResponseDto expected = new RagQueryResponseDto(List.of(), false, null);
    CurrentUserPrincipal teacher = new CurrentUserPrincipal(1L, "teacher", Role.TEACHER);

    when(facade.search(request)).thenReturn(expected);
    when(throttle.tryAcquireRetrievalRequest(teacher.userId())).thenReturn(true);

    RagQueryResponseDto response = controller.query(request, teacher);

    assertThat(response).isSameAs(expected);
    verify(facade).search(request);
  }

  @Test
  void rejectsRetrievalBeforeCallingRagWhenTheAccountLimitIsReached() {
    RagFacade facade = mock(RagFacade.class);
    StudentWriteThrottle throttle = mock(StudentWriteThrottle.class);
    RagController controller = new RagController(facade, throttle);
    RagQueryRequestDto request = new RagQueryRequestDto("循环", 6, null, null, 0.3);
    CurrentUserPrincipal teacher = new CurrentUserPrincipal(1L, "teacher", Role.TEACHER);
    when(throttle.tryAcquireRetrievalRequest(teacher.userId())).thenReturn(false);

    assertThatThrownBy(() -> controller.query(request, teacher))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> assertThat(error.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));

    verify(facade, never()).search(request);
  }
}
