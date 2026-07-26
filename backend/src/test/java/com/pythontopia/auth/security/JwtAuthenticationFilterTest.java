package com.pythontopia.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pythontopia.auth.domain.UserAccount;
import com.pythontopia.auth.repository.UserRepository;
import jakarta.servlet.FilterChain;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 每次请求的鉴权都必须复核「会话是否仍然有效」：账号停用、用户被删、会话代次被自增（吊销）时，
 * 即使 JWT 签名与有效期都没问题也不得写入 SecurityContext——否则泄露的 token 在 TTL 内不可撤销。
 */
class JwtAuthenticationFilterTest {

  private static final CurrentUserPrincipal PRINCIPAL =
      new CurrentUserPrincipal(9L, "stu01", Role.STUDENT, 2);

  private JwtService jwtService;
  private UserRepository userRepository;
  private JwtAuthenticationFilter filter;

  @BeforeEach
  void setUp() {
    jwtService = mock(JwtService.class);
    userRepository = mock(UserRepository.class);
    filter = new JwtAuthenticationFilter(jwtService, userRepository);
    when(jwtService.parse("live-token")).thenReturn(PRINCIPAL);
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private static UserAccount account(String status, int tokenVersion) {
    return new UserAccount(9L, "stu01", "Stu", "hash", null, Role.STUDENT, status, tokenVersion);
  }

  /** 跑一次过滤器（可选带 Bearer 头），返回它写入 SecurityContext 的登录态（未认证则 null）。 */
  private Authentication runFilter(String bearerToken) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    if (bearerToken != null) {
      request.addHeader("Authorization", "Bearer " + bearerToken);
    }
    filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));
    return SecurityContextHolder.getContext().getAuthentication();
  }

  @Test
  @DisplayName("活跃账号 + 代次一致：写入登录态与角色权限")
  void authenticatesLiveSession() throws Exception {
    when(userRepository.findById(9L))
        .thenReturn(Optional.of(account(UserAccount.STATUS_ACTIVE, 2)));

    Authentication authentication = runFilter("live-token");

    assertThat(authentication).isNotNull();
    assertThat(authentication.getPrincipal()).isEqualTo(PRINCIPAL);
    assertThat(authentication.getAuthorities())
        .extracting(Object::toString)
        .containsExactly("ROLE_STUDENT");
  }

  @Test
  @DisplayName("账号已停用：不写入登录态（后续由 entry point 回 401）")
  void rejectsDisabledAccount() throws Exception {
    when(userRepository.findById(9L)).thenReturn(Optional.of(account("DISABLED", 2)));

    assertThat(runFilter("live-token")).isNull();
  }

  @Test
  @DisplayName("会话代次已自增（已吊销）：不写入登录态")
  void rejectsRevokedTokenVersion() throws Exception {
    when(userRepository.findById(9L))
        .thenReturn(Optional.of(account(UserAccount.STATUS_ACTIVE, 3)));

    assertThat(runFilter("live-token")).isNull();
  }

  @Test
  @DisplayName("用户已被删除：不写入登录态")
  void rejectsMissingUser() throws Exception {
    when(userRepository.findById(9L)).thenReturn(Optional.empty());

    assertThat(runFilter("live-token")).isNull();
  }

  @Test
  @DisplayName("无 Authorization 头：不解析 token，也不查库")
  void skipsRequestsWithoutBearerToken() throws Exception {
    assertThat(runFilter(null)).isNull();

    verifyNoInteractions(userRepository);
  }
}
