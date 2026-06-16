package com.pythontopia.auth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pythontopia.auth.dto.LoginRequest;
import com.pythontopia.auth.repository.UserRepository;
import com.pythontopia.auth.security.JwtService;
import com.pythontopia.auth.security.TeacherInviteProperties;
import com.pythontopia.common.error.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceSecurityTest {

  @Test
  void absentUsernameStillPerformsPasswordHashComparison() {
    UserRepository repository = mock(UserRepository.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    JwtService jwtService = mock(JwtService.class);
    when(encoder.encode("pythontopia-login-timing-dummy")).thenReturn("dummy-password-hash");
    when(repository.findByUsername("absent-user")).thenReturn(Optional.empty());
    when(encoder.matches("wrong-password", "dummy-password-hash")).thenReturn(false);
    AuthService service =
        new AuthService(repository, encoder, jwtService, new TeacherInviteProperties("invite"));

    assertThatThrownBy(
            () -> service.login(new LoginRequest("absent-user", "wrong-password")))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            exception ->
                org.assertj.core.api.Assertions.assertThat(((BusinessException) exception).code())
                    .isEqualTo("BAD_CREDENTIALS"));

    verify(encoder).matches("wrong-password", "dummy-password-hash");
  }
}
