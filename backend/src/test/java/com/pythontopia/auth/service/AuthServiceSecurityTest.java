package com.pythontopia.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pythontopia.auth.domain.UserAccount;
import com.pythontopia.auth.dto.LoginRequest;
import com.pythontopia.auth.dto.LoginResponse;
import com.pythontopia.auth.dto.RegisterRequest;
import com.pythontopia.auth.repository.UserRepository;
import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.JwtService;
import com.pythontopia.auth.security.JwtValidationException;
import com.pythontopia.auth.security.Role;
import com.pythontopia.auth.security.TeacherInviteProperties;
import com.pythontopia.common.error.BusinessException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * AuthService 的安全契约回归：自助注册角色白名单、停用账号不得登录 / 续期、会话代次（吊销）
 * 不匹配的 token 不得换新。
 */
class AuthServiceSecurityTest {

  private static final String DUMMY_HASH = "dummy-password-hash";
  private static final String VALID_TEACHER_INVITE = "7ec856ee4f514a66ad5bf1f4f7a9c193";
  private static final Instant EXPIRES_AT = Instant.parse("2026-05-21T08:00:00Z");

  private UserRepository repository;
  private PasswordEncoder encoder;
  private JwtService jwtService;
  private AuthService service;

  @BeforeEach
  void setUp() {
    repository = mock(UserRepository.class);
    encoder = mock(PasswordEncoder.class);
    jwtService = mock(JwtService.class);
    when(encoder.encode("pythontopia-login-timing-dummy")).thenReturn(DUMMY_HASH);
    service =
        new AuthService(
            repository, encoder, jwtService, new TeacherInviteProperties(VALID_TEACHER_INVITE));
  }

  private static UserAccount user(Role role, String status, int tokenVersion) {
    return new UserAccount(9L, "stu01", "Stu", "stored-hash", null, role, status, tokenVersion);
  }

  private static RegisterRequest registerAs(Role role, String inviteCode) {
    return new RegisterRequest("newbie", "P@ssw0rd1", "Newbie", role, null, inviteCode);
  }

  private static void assertBusiness(Throwable thrown, HttpStatus status, String code) {
    assertThat(thrown).isInstanceOf(BusinessException.class);
    BusinessException exception = (BusinessException) thrown;
    assertThat(exception.status()).isEqualTo(status);
    assertThat(exception.code()).isEqualTo(code);
  }

  @Nested
  @DisplayName("register：自助注册的角色白名单")
  class RegisterRoleWhitelist {

    @Test
    @DisplayName("匿名请求体自称 ADMIN 一律 403，且不落库")
    void adminSelfRegistrationIsRejected() {
      assertBusiness(
          catchThrowable(
              () -> service.register(registerAs(Role.ADMIN, null))),
          HttpStatus.FORBIDDEN,
          "FORBIDDEN");

      verify(repository, never()).save(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("带上教师邀请码也不能借道注册 ADMIN")
    void adminSelfRegistrationIsRejectedEvenWithTeacherInviteCode() {
      assertThatThrownBy(() -> service.register(registerAs(Role.ADMIN, "invite")))
          .isInstanceOf(BusinessException.class);

      verify(repository, never()).save(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("STUDENT 无需邀请码即可注册")
    void studentSelfRegistrationIsAllowed() {
      when(encoder.encode("P@ssw0rd1")).thenReturn("encoded");
      when(repository.save("newbie", "Newbie", "encoded", null, Role.STUDENT))
          .thenReturn(user(Role.STUDENT, UserAccount.STATUS_ACTIVE, 0));

      assertThat(service.register(registerAs(Role.STUDENT, null)).role()).isEqualTo(Role.STUDENT);
    }

    @Test
    @DisplayName("TEACHER 仍受邀请码门禁：错误邀请码 403")
    void teacherSelfRegistrationStillRequiresInviteCode() {
      assertBusiness(
          catchThrowable(
              () -> service.register(registerAs(Role.TEACHER, "wrong"))),
          HttpStatus.FORBIDDEN,
          "FORBIDDEN");

      verify(repository, never()).save(anyString(), anyString(), anyString(), any(), any());
    }
  }

  @Nested
  @DisplayName("login：口令、账号状态与会话代次")
  class Login {

    @Test
    @DisplayName("用户名不存在时仍执行一次哈希比对（消除时序侧信道）")
    void absentUsernameStillPerformsPasswordHashComparison() {
      when(repository.findByUsername("absent-user")).thenReturn(Optional.empty());
      when(encoder.matches("wrong-password", DUMMY_HASH)).thenReturn(false);

      assertBusiness(
          catchThrowable(
              () -> service.login(new LoginRequest("absent-user", "wrong-password"))),
          HttpStatus.UNAUTHORIZED,
          "BAD_CREDENTIALS");

      verify(encoder).matches("wrong-password", DUMMY_HASH);
    }

    @Test
    @DisplayName("停用账号即使口令正确也不得登录，且不签发 token")
    void disabledAccountCannotLogIn() {
      when(repository.findByUsername("stu01"))
          .thenReturn(Optional.of(user(Role.STUDENT, "DISABLED", 0)));
      when(encoder.matches("P@ssw0rd1", "stored-hash")).thenReturn(true);

      assertBusiness(
          catchThrowable(
              () -> service.login(new LoginRequest("stu01", "P@ssw0rd1"))),
          HttpStatus.UNAUTHORIZED,
          "ACCOUNT_DISABLED");

      verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("登录签发的 token 携带账号当前会话代次")
    void issuedTokenCarriesCurrentTokenVersion() {
      when(repository.findByUsername("stu01"))
          .thenReturn(Optional.of(user(Role.STUDENT, UserAccount.STATUS_ACTIVE, 3)));
      when(encoder.matches("P@ssw0rd1", "stored-hash")).thenReturn(true);
      when(jwtService.issue(new CurrentUserPrincipal(9L, "stu01", Role.STUDENT, 3)))
          .thenReturn(new JwtService.IssuedToken("signed-token", EXPIRES_AT));

      LoginResponse response = service.login(new LoginRequest("stu01", "P@ssw0rd1"));

      assertThat(response.token()).isEqualTo("signed-token");
      verify(jwtService).issue(new CurrentUserPrincipal(9L, "stu01", Role.STUDENT, 3));
    }
  }

  @Nested
  @DisplayName("refresh：续期不得绕过吊销")
  class Refresh {

    @Test
    @DisplayName("非法 / 过期 token 换不到新 token")
    void invalidTokenIsRejected() {
      when(jwtService.parse("bad-token")).thenThrow(new JwtValidationException("Token expired"));

      assertBusiness(
          catchThrowable(() -> service.refresh("bad-token")),
          HttpStatus.UNAUTHORIZED,
          "REFRESH_TOKEN_INVALID");
    }

    @Test
    @DisplayName("token 主体已不存在时拒绝续期")
    void missingSubjectIsRejected() {
      when(jwtService.parse("orphan-token"))
          .thenReturn(new CurrentUserPrincipal(9L, "stu01", Role.STUDENT, 0));
      when(repository.findByUsername("stu01")).thenReturn(Optional.empty());

      assertBusiness(
          catchThrowable(() -> service.refresh("orphan-token")),
          HttpStatus.UNAUTHORIZED,
          "REFRESH_TOKEN_INVALID");
    }

    @Test
    @DisplayName("会话代次已自增（已吊销）的旧 token 换不到新 token")
    void revokedTokenCannotBeRefreshed() {
      when(jwtService.parse("stale-token"))
          .thenReturn(new CurrentUserPrincipal(9L, "stu01", Role.STUDENT, 0));
      when(repository.findByUsername("stu01"))
          .thenReturn(Optional.of(user(Role.STUDENT, UserAccount.STATUS_ACTIVE, 1)));

      assertBusiness(
          catchThrowable(() -> service.refresh("stale-token")),
          HttpStatus.UNAUTHORIZED,
          "REFRESH_TOKEN_INVALID");

      verify(jwtService, never()).issue(any());
    }

    @Test
    @DisplayName("账号已停用时拒绝续期")
    void disabledAccountCannotRefresh() {
      when(jwtService.parse("valid-token"))
          .thenReturn(new CurrentUserPrincipal(9L, "stu01", Role.STUDENT, 0));
      when(repository.findByUsername("stu01"))
          .thenReturn(Optional.of(user(Role.STUDENT, "DISABLED", 0)));

      assertBusiness(
          catchThrowable(() -> service.refresh("valid-token")),
          HttpStatus.UNAUTHORIZED,
          "ACCOUNT_DISABLED");

      verify(jwtService, never()).issue(any());
    }

    @Test
    @DisplayName("活跃账号 + 代次一致时正常续期，新 token 沿用当前代次")
    void activeAccountRefreshesSuccessfully() {
      when(jwtService.parse("valid-token"))
          .thenReturn(new CurrentUserPrincipal(9L, "stu01", Role.STUDENT, 2));
      when(repository.findByUsername("stu01"))
          .thenReturn(Optional.of(user(Role.STUDENT, UserAccount.STATUS_ACTIVE, 2)));
      when(jwtService.issue(new CurrentUserPrincipal(9L, "stu01", Role.STUDENT, 2)))
          .thenReturn(new JwtService.IssuedToken("fresh-token", EXPIRES_AT));

      LoginResponse response = service.refresh("valid-token");

      assertThat(response.token()).isEqualTo("fresh-token");
      assertThat(response.expiresAt()).isEqualTo(EXPIRES_AT);
      assertThat(response.user().username()).isEqualTo("stu01");
    }
  }
}
