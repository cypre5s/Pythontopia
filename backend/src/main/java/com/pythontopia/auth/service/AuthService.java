package com.pythontopia.auth.service;

import com.pythontopia.auth.domain.UserAccount;
import com.pythontopia.auth.dto.LoginRequest;
import com.pythontopia.auth.dto.LoginResponse;
import com.pythontopia.auth.dto.RegisterRequest;
import com.pythontopia.auth.dto.UserDto;
import com.pythontopia.auth.repository.UserRepository;
import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.JwtService;
import com.pythontopia.auth.security.Role;
import com.pythontopia.auth.security.TeacherInviteProperties;
import com.pythontopia.common.error.BusinessException;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

  private static final String LOGIN_TIMING_DUMMY_PASSWORD = "pythontopia-login-timing-dummy";

  /**
   * 自助注册（{@code POST /api/auth/register} 是 permitAll）只开放这两个角色。ADMIN 绕过
   * ClassroomGuard 的归属校验、可读写任意班级后台，绝不能由匿名请求体自选，只能由运维直接写库授予。
   */
  private static final Set<Role> SELF_REGISTRABLE_ROLES = Set.of(Role.STUDENT, Role.TEACHER);

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final TeacherInviteProperties teacherInviteProperties;
  private final String loginTimingDummyHash;

  public AuthService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      TeacherInviteProperties teacherInviteProperties) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.teacherInviteProperties = teacherInviteProperties;
    this.loginTimingDummyHash = passwordEncoder.encode(LOGIN_TIMING_DUMMY_PASSWORD);
  }

  public UserDto register(RegisterRequest request) {
    if (!SELF_REGISTRABLE_ROLES.contains(request.role())) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "Role is not open for self-registration");
    }
    if (request.role() == Role.TEACHER
        && (teacherInviteProperties.teacherInviteCode() == null
            || teacherInviteProperties.teacherInviteCode().isBlank()
            || !teacherInviteProperties.teacherInviteCode().equals(request.teacherInviteCode()))) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Invalid teacher invite code");
    }
    try {
      UserAccount user =
          userRepository.save(
              request.username(),
              request.displayName(),
              passwordEncoder.encode(request.password()),
              request.email(),
              request.role());
      return toDto(user);
    } catch (DuplicateKeyException exception) {
      throw new BusinessException(HttpStatus.CONFLICT, "USERNAME_CONFLICT", "Username or email exists");
    }
  }

  public LoginResponse login(LoginRequest request) {
    UserAccount user = userRepository.findByUsername(request.username()).orElse(null);
    if (user == null) {
      passwordEncoder.matches(request.password(), loginTimingDummyHash);
      throw new BusinessException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Bad credentials");
    }
    if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
      throw new BusinessException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Bad credentials");
    }
    // 状态校验放在口令校验之后：停用账号不会成为「用户名是否存在」的探测面。
    requireActive(user);
    return issueFor(user);
  }

  /**
   * 用现有有效 token 重新签发 access token。当前契约有意把 access token 兼作 refresh 源，
   * 故调用方须回传未过期的 access token；非法或过期 token 抛 {@code 401 UNAUTHORIZED}。
   *
   * <p>续期不是无条件的：已停用账号、以及会话代次已被自增（吊销）的 token 都会在这里被拒，
   * 否则一枚旧 token 可以把自己「洗」成携带新代次的新 token，绕过吊销。
   */
  public LoginResponse refresh(String token) {
    CurrentUserPrincipal principal;
    try {
      principal = jwtService.parse(token);
    } catch (Exception exception) {
      throw new BusinessException(
          HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID", "Refresh token is invalid or expired");
    }
    UserAccount user =
        userRepository
            .findById(principal.userId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID", "Token subject not found"));
    if (user.tokenVersion() != principal.tokenVersion()) {
      throw new BusinessException(
          HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID", "Refresh token has been revoked");
    }
    requireActive(user);
    return issueFor(user);
  }

  /** Revokes every existing token for the authenticated user, including the token used here. */
  public void logout(CurrentUserPrincipal principal) {
    if (principal == null) {
      throw new BusinessException(
          HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required");
    }
    userRepository.bumpTokenVersion(principal.userId());
  }

  public UserDto toDto(UserAccount user) {
    return new UserDto(user.id(), user.username(), user.displayName(), user.role());
  }

  private void requireActive(UserAccount user) {
    if (!user.isActive()) {
      throw new BusinessException(
          HttpStatus.UNAUTHORIZED, "ACCOUNT_DISABLED", "Account is disabled");
    }
  }

  private LoginResponse issueFor(UserAccount user) {
    JwtService.IssuedToken issued =
        jwtService.issue(
            new CurrentUserPrincipal(
                user.id(), user.username(), user.role(), user.tokenVersion()));
    return new LoginResponse(issued.token(), issued.expiresAt(), toDto(user));
  }
}
