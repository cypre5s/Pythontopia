package com.pythontopia.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.tutor.TutorDailyRequestLimitService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.core.Ordered;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AiBudgetMethodInterceptorTest {

  @Mock TutorDailyRequestLimitService requestLimitService;
  private AiBudgetMethodInterceptor interceptor;

  @BeforeEach
  void setUp() {
    interceptor = new AiBudgetMethodInterceptor(requestLimitService);
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
    GeneratedEndpoints controller = proxiedEndpoints();

    assertThat(controller.tutor()).isEqualTo("ok");
    assertThat(controller.expedition()).isEqualTo("ok");

    verify(requestLimitService, times(2)).reserve(17L);
  }

  @Test
  void unmarkedEndpointsDoNotSpendAiBudget() throws Exception {
    assertThat(proxiedEndpoints().status()).isEqualTo("ok");
    verifyNoInteractions(requestLimitService);
  }

  @Test
  void markedEndpointRequiresAnAuthenticatedAccount() {
    assertThatThrownBy(() -> proxiedEndpoints().tutor())
        .isInstanceOf(com.pythontopia.common.error.BusinessException.class)
        .hasMessageContaining("需要登录");
    verifyNoInteractions(requestLimitService);
  }

  @Test
  void wrongRoleDoesNotSpendAiBudget() {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(
            new CurrentUserPrincipal(17L, "teacher", Role.TEACHER), null, List.of()));

    assertThatThrownBy(() -> proxiedEndpoints().studentOnly())
        .isInstanceOf(com.pythontopia.common.error.BusinessException.class)
        .hasMessageContaining("无权");
    verifyNoInteractions(requestLimitService);
  }

  private GeneratedEndpoints proxiedEndpoints() {
    DefaultPointcutAdvisor advisor =
        new DefaultPointcutAdvisor(
            AnnotationMatchingPointcut.forMethodAnnotation(AiBudgeted.class), interceptor);
    advisor.setOrder(Ordered.LOWEST_PRECEDENCE);
    ProxyFactory factory = new ProxyFactory(new GeneratedEndpoints());
    factory.setProxyTargetClass(true);
    factory.addAdvisor(advisor);
    return (GeneratedEndpoints) factory.getProxy();
  }

  static class GeneratedEndpoints {
    @AiBudgeted public String tutor() { return "ok"; }
    @AiBudgeted public String expedition() { return "ok"; }
    @AiBudgeted(roles = Role.STUDENT) public String studentOnly() { return "ok"; }
    public String status() { return "ok"; }
  }
}
