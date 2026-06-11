package com.pythontopia.intent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.intent.dto.IntentRouteRequest;
import com.pythontopia.intent.dto.IntentRouteResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** 纯单元测试：验证百晓工坊意图路由入口的"仅学生可用"鉴权与对 service 的委托，不启动 Spring 上下文。 */
class IntentControllerTest {

  private static IntentRouteRequest sampleRequest() {
    return new IntentRouteRequest(
        "帮我做个 for 循环的思维导图",
        List.of(),
        List.of(),
        null,
        List.of(),
        List.of(),
        null,
        List.of(),
        List.of(),
        null);
  }

  @Test
  void rejectsNonStudentWithForbidden() {
    IntentService service = mock(IntentService.class);
    IntentController controller = new IntentController(service);
    CurrentUserPrincipal teacher = new CurrentUserPrincipal(2L, "teacher", Role.TEACHER);

    assertThatThrownBy(() -> controller.route(teacher, sampleRequest()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("仅学生可使用百晓工坊")
        .extracting(ex -> ((BusinessException) ex).status())
        .isEqualTo(HttpStatus.FORBIDDEN);

    verifyNoInteractions(service);
  }

  @Test
  void studentDelegatesToService() {
    IntentService service = mock(IntentService.class);
    IntentRouteRequest request = sampleRequest();
    CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "stu", Role.STUDENT);
    IntentRouteResponse expected =
        new IntentRouteResponse("MINDMAP", List.of("PY43-KC-001"), "for 循环", 0.9, "ok", "", null);
    when(service.route(eq(student), eq(request))).thenReturn(expected);
    IntentController controller = new IntentController(service);

    IntentRouteResponse actual = controller.route(student, request);

    assertThat(actual).isSameAs(expected);
  }
}
