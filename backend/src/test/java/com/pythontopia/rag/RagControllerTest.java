package com.pythontopia.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.rag.dto.RagQueryRequestDto;
import com.pythontopia.rag.dto.RagQueryResponseDto;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagControllerTest {

  @Test
  void queryForwardsFullRequestWithoutDroppingGraphOptions() {
    RagFacade facade = mock(RagFacade.class);
    RagController controller = new RagController(facade);
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

    RagQueryResponseDto response = controller.query(request, teacher);

    assertThat(response).isSameAs(expected);
    verify(facade).search(request);
  }
}
