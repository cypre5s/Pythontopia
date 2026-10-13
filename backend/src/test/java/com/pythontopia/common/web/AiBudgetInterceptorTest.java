package com.pythontopia.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.tutor.TutorDailyRequestLimitService;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

@ExtendWith(MockitoExtension.class)
class AiBudgetInterceptorTest {

  @Mock TutorDailyRequestLimitService requestLimitService;
  private AiBudgetInterceptor interceptor;

  @BeforeEach
  void setUp() {
    interceptor = new AiBudgetInterceptor(requestLimitService);
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void separateAiEndpointsReserveFromTheSameAccountBudget() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(
            new CurrentUserPrincipal(17L, "student", Role.STUDENT), null, List.of()));
    GeneratedEndpoints controller = new GeneratedEndpoints();

    assertThat(intercept(controller, "tutor")).isTrue();
    assertThat(intercept(controller, "expedition")).isTrue();

    verify(requestLimitService, times(2)).reserve(17L);
  }

  @Test
  void unmarkedEndpointsDoNotSpendAiBudget() throws Exception {
    assertThat(intercept(new GeneratedEndpoints(), "status")).isTrue();
    verifyNoInteractions(requestLimitService);
  }

  @Test
  void markedEndpointRequiresAnAuthenticatedAccount() {
    assertThatThrownBy(() -> intercept(new GeneratedEndpoints(), "tutor"))
        .isInstanceOf(com.pythontopia.common.error.BusinessException.class)
        .hasMessageContaining("需要登录");
    verifyNoInteractions(requestLimitService);
  }

  private boolean intercept(Object controller, String methodName) throws Exception {
    Method method = controller.getClass().getMethod(methodName);
    return interceptor.preHandle(
        new MockHttpServletRequest(), new MockHttpServletResponse(), new HandlerMethod(controller, method));
  }

  static class GeneratedEndpoints {
    @AiBudgeted public void tutor() {}
    @AiBudgeted public void expedition() {}
    public void status() {}
  }
}
