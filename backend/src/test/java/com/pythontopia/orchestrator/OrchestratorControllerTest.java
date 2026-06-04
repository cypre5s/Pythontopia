package com.pythontopia.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.orchestrator.dto.OrchestratorTurnRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 纯单元测试：验证学习总管入口的"仅学生可用"鉴权与对 service 的委托，不启动 Spring 上下文。 */
class OrchestratorControllerTest {

  private static OrchestratorTurnRequest sampleRequest() {
    return new OrchestratorTurnRequest(
        "讲讲 for 循环", null, List.of(), null, null, null, null, null);
  }

  @Test
  void rejectsNonStudentWithForbidden() {
    OrchestratorService service = mock(OrchestratorService.class);
    OrchestratorController controller = new OrchestratorController(service);
    CurrentUserPrincipal teacher = new CurrentUserPrincipal(2L, "teacher", Role.TEACHER);

    assertThatThrownBy(() -> controller.turnStream(teacher, sampleRequest()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("仅学生可使用学习总管")
        .extracting(ex -> ((BusinessException) ex).status())
        .isEqualTo(HttpStatus.FORBIDDEN);

    verifyNoInteractions(service);
  }

  @Test
  void studentDelegatesToServiceWithUserId() {
    OrchestratorService service = mock(OrchestratorService.class);
    OrchestratorTurnRequest request = sampleRequest();
    SseEmitter expected = new SseEmitter();
    when(service.turn(eq(7L), eq(request))).thenReturn(expected);
    OrchestratorController controller = new OrchestratorController(service);
    CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "stu", Role.STUDENT);

    SseEmitter actual = controller.turnStream(student, request);

    assertThat(actual).isSameAs(expected);
  }
}
