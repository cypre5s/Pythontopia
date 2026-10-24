package com.pythontopia.auth.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pythontopia.auth.repository.UserRepository;
import com.pythontopia.auth.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 初始管理员自举的分支逻辑（用 mock 仓储，不需要数据库）。 */
class BootstrapAdminInitializerTest {

  private final UserRepository userRepository = mock(UserRepository.class);
  private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

  @Test
  void doesNothingWhenBootstrapIsNotConfigured() {
    new BootstrapAdminInitializer(userRepository, passwordEncoder, "", "", "管理员")
        .ensureAdminAccount();

    verifyNoInteractions(userRepository);
  }

  @Test
  void doesNothingWhenOnlyUsernameIsConfigured() {
    new BootstrapAdminInitializer(userRepository, passwordEncoder, "admin", "", "管理员")
        .ensureAdminAccount();

    verifyNoInteractions(userRepository);
  }

  @Test
  void rejectsTooShortPassword() {
    new BootstrapAdminInitializer(userRepository, passwordEncoder, "admin", "short", "管理员")
        .ensureAdminAccount();

    verifyNoInteractions(userRepository);
  }

  @Test
  void skipsWhenAdminAlreadyExists() {
    when(userRepository.existsAdmin()).thenReturn(true);

    new BootstrapAdminInitializer(userRepository, passwordEncoder, "admin", "s3cret-pass", "管理员")
        .ensureAdminAccount();

    verify(userRepository, never()).save(any(), any(), any(), any(), any());
    verify(userRepository, never()).promoteToAdmin(anyString());
  }

  @Test
  void promotesExistingUsernameInsteadOfCreatingDuplicate() {
    when(userRepository.existsAdmin()).thenReturn(false);
    when(userRepository.existsByUsername("admin")).thenReturn(true);

    new BootstrapAdminInitializer(userRepository, passwordEncoder, "admin", "s3cret-pass", "管理员")
        .ensureAdminAccount();

    verify(userRepository).promoteToAdmin("admin");
    verify(userRepository, never()).save(any(), any(), any(), any(), any());
  }

  @Test
  void createsAdminWithEncodedPasswordWhenDatabaseIsEmpty() {
    when(userRepository.existsAdmin()).thenReturn(false);
    when(userRepository.existsByUsername("admin")).thenReturn(false);
    when(passwordEncoder.encode("s3cret-pass")).thenReturn("HASHED");

    new BootstrapAdminInitializer(userRepository, passwordEncoder, "admin", "s3cret-pass", "管理员")
        .ensureAdminAccount();

    verify(userRepository).save("admin", "管理员", "HASHED", null, Role.ADMIN);
  }

  @Test
  void fallsBackToDefaultDisplayNameWhenBlank() {
    when(userRepository.existsAdmin()).thenReturn(false);
    when(userRepository.existsByUsername("root")).thenReturn(false);
    when(passwordEncoder.encode("s3cret-pass")).thenReturn("HASHED");

    new BootstrapAdminInitializer(userRepository, passwordEncoder, " root ", " s3cret-pass ", " ")
        .ensureAdminAccount();

    verify(userRepository).save("root", "管理员", "HASHED", null, Role.ADMIN);
  }
}
