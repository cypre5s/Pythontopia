package com.pythontopia.common.web;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.tutor.TutorDailyRequestLimitService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** Applies the shared account cap to user-triggered model endpoints, including failed requests. */
public class AiBudgetInterceptor implements HandlerInterceptor {

  private final TutorDailyRequestLimitService requestLimitService;

  public AiBudgetInterceptor(TutorDailyRequestLimitService requestLimitService) {
    this.requestLimitService = requestLimitService;
  }

  @Override
  public boolean preHandle(
      jakarta.servlet.http.HttpServletRequest request,
      jakarta.servlet.http.HttpServletResponse response,
      Object handler) {
    if (!(handler instanceof HandlerMethod method)
        || !method.hasMethodAnnotation(AiBudgeted.class)) {
      return true;
    }
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !(authentication.getPrincipal() instanceof CurrentUserPrincipal user)) {
      throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "需要登录后使用 AI 功能");
    }
    requestLimitService.reserve(user.userId());
    return true;
  }
}
