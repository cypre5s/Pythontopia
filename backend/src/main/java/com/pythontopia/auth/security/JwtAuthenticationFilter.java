package com.pythontopia.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtService jwtService;

  public JwtAuthenticationFilter(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  /**
   * SSE 端点（如 {@code POST /api/tutor/ask}）返回 {@code SseEmitter} 后会触发 ASYNC 再分发。
   * {@code OncePerRequestFilter} 默认在 ASYNC 分发上跳过本过滤器（{@code shouldNotFilterAsyncDispatch}
   * 默认 true），而项目是 STATELESS 无 SecurityContext 仓库，于是 ASYNC 分发上下文为空，
   * Spring Security 的 AuthorizationFilter 会把异步分发判成未认证而回 401。返回 false 让本过滤器
   * 在 ASYNC 分发上也重新从 Authorization 头解析并恢复认证。
   */
  @Override
  protected boolean shouldNotFilterAsyncDispatch() {
    return false;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String authorization = request.getHeader("Authorization");
    if (authorization != null && authorization.startsWith("Bearer ")) {
      CurrentUserPrincipal principal = jwtService.parse(authorization.substring(7));
      UsernamePasswordAuthenticationToken authentication =
          new UsernamePasswordAuthenticationToken(
              principal,
              null,
              List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
      SecurityContextHolder.getContext().setAuthentication(authentication);
    }
    filterChain.doFilter(request, response);
  }
}
