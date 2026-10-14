package com.pythontopia.common.web;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.tutor.TutorDailyRequestLimitService;
import java.util.Arrays;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

/** Reserves the shared AI budget after MVC validation and before invoking the controller. */
public class AiBudgetMethodInterceptor implements MethodInterceptor {

  private final TutorDailyRequestLimitService requestLimitService;

  public AiBudgetMethodInterceptor(TutorDailyRequestLimitService requestLimitService) {
    this.requestLimitService = requestLimitService;
  }

  @Override
  public Object invoke(MethodInvocation invocation) throws Throwable {
    AiBudgeted budgeted = invocation.getMethod().getAnnotation(AiBudgeted.class);
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !(authentication.getPrincipal() instanceof CurrentUserPrincipal user)) {
      throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "需要登录后使用 AI 功能");
    }
    if (budgeted != null
        && budgeted.roles().length > 0
        && Arrays.stream(budgeted.roles()).noneMatch(role -> role == user.role())) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前账号无权使用此 AI 功能");
    }
    requestLimitService.reserve(user.userId());
    return invocation.proceed();
  }
}
