package com.pythontopia.npc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.npc.dto.NpcGreetingResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class NpcGreetingControllerTest {

  private final NpcGreetingService greetingService = mock(NpcGreetingService.class);
  private final NpcGreetingController controller = new NpcGreetingController(greetingService);

  private static CurrentUserPrincipal principal(long userId, Role role) {
    return new CurrentUserPrincipal(userId, "user-" + userId, role);
  }

  @Test
  void studentGetsGreetingDelegatedToService() {
    NpcGreetingResponse expected =
        NpcGreetingResponse.personalized("你好同学", true, List.of(9L));
    when(greetingService.greeting(7L, "foreman_bug")).thenReturn(expected);

    NpcGreetingResponse actual = controller.greeting(principal(7L, Role.STUDENT), "foreman_bug");

    assertThat(actual).isSameAs(expected);
    verify(greetingService).greeting(7L, "foreman_bug");
  }

  @Test
  void teacherIsForbidden() {
    CurrentUserPrincipal teacher = principal(1L, Role.TEACHER);

    assertThatThrownBy(() -> controller.greeting(teacher, "foreman_bug"))
        .isInstanceOf(BusinessException.class);
    verify(greetingService, never()).greeting(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void nullPrincipalIsForbidden() {
    assertThatThrownBy(() -> controller.greeting(null, "foreman_bug"))
        .isInstanceOf(BusinessException.class);
  }
}
