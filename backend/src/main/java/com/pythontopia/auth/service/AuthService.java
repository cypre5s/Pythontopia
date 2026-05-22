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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final TeacherInviteProperties teacherInviteProperties;

  public AuthService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      TeacherInviteProperties teacherInviteProperties) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.teacherInviteProperties = teacherInviteProperties;
  }

  public UserDto register(RegisterRequest request) {
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
    UserAccount user =
        userRepository
            .findByUsername(request.username())
            .orElseThrow(
                () -> new BusinessException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Bad credentials"));
    if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
      throw new BusinessException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Bad credentials");
    }
    JwtService.IssuedToken issued =
        jwtService.issue(new CurrentUserPrincipal(user.id(), user.username(), user.role()));
    return new LoginResponse(issued.token(), issued.expiresAt(), toDto(user));
  }

  /**
   * Reissue an access token from an existing valid token. The contract intentionally treats the
   * access token as the refresh source — Stage 02 has not separated the two — so callers must hand
   * back a non-expired access token. Invalid / expired tokens fail with {@code 401 UNAUTHORIZED}.
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
            .findByUsername(principal.username())
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID", "Token subject not found"));
    JwtService.IssuedToken issued =
        jwtService.issue(new CurrentUserPrincipal(user.id(), user.username(), user.role()));
    return new LoginResponse(issued.token(), issued.expiresAt(), toDto(user));
  }

  public UserDto toDto(UserAccount user) {
    return new UserDto(user.id(), user.username(), user.displayName(), user.role());
  }
}
