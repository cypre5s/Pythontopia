package com.pythontopia.auth.security;

import com.pythontopia.auth.domain.UserAccount;
import com.pythontopia.auth.repository.UserRepository;
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
  private final UserRepository userRepository;

  public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
    this.jwtService = jwtService;
    this.userRepository = userRepository;
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
      try {
        CurrentUserPrincipal principal = jwtService.parse(authorization.substring(7));
        if (isSessionAlive(principal)) {
          UsernamePasswordAuthenticationToken authentication =
              new UsernamePasswordAuthenticationToken(
                  principal,
                  null,
                  List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
          SecurityContextHolder.getContext().setAuthentication(authentication);
        }
      } catch (JwtValidationException exception) {
        SecurityContextHolder.clearContext();
      }
    }
    filterChain.doFilter(request, response);
  }

  /**
   * 签名有效 ≠ 会话仍然有效。账号被停用（{@code users.status != 'ACTIVE'}）、用户被删除，或会话代次
   * 已自增（改密 / 手工吊销，见 V188）时都必须拒绝，否则一枚泄露的 token 在 TTL 内不可撤销。
   *
   * <p>不认证即不写入 SecurityContext：请求随后由 SecurityConfig 的 authenticationEntryPoint 统一回
   * 401 UNAUTHORIZED，与「没带 token」同构，不额外泄露账号状态。
   */
  private boolean isSessionAlive(CurrentUserPrincipal principal) {
    return userRepository
        .findById(principal.userId())
        .filter(UserAccount::isActive)
        .filter(user -> user.tokenVersion() == principal.tokenVersion())
        .isPresent();
  }
}
