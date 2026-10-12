package com.pythontopia.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AdminUserServiceTest {

  private final AdminUserRepository repository = mock(AdminUserRepository.class);
  private final AdminUserService service = new AdminUserService(repository);

  @Test
  void nonAdminCannotListUsers() {
    CurrentUserPrincipal student = new CurrentUserPrincipal(2L, "student", Role.STUDENT);

    assertThatThrownBy(() -> service.page(student, 0, 20, null, null, null))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            exception -> {
              BusinessException businessException = (BusinessException) exception;
              assertThat(businessException.status()).isEqualTo(HttpStatus.FORBIDDEN);
              assertThat(businessException.code()).isEqualTo("ADMIN_REQUIRED");
            });

    verifyNoInteractions(repository);
  }

  @Test
  void adminListsUsersWithRequestedFiltersAndPage() {
    CurrentUserPrincipal admin = new CurrentUserPrincipal(1L, "admin", Role.ADMIN);
    AdminUserDto user = new AdminUserDto(7L, "stu", "Student", Role.STUDENT, "ACTIVE");
    AdminUserPageDto expected = new AdminUserPageDto(List.of(user), 0, 20, 1, 1);
    when(repository.page(0, 20, "stu", AdminUserStatus.ACTIVE, Role.TEACHER)).thenReturn(expected);

    assertThat(service.page(admin, 0, 20, " stu ", AdminUserStatus.ACTIVE, Role.TEACHER))
        .isSameAs(expected);
    verify(repository).page(0, 20, "stu", AdminUserStatus.ACTIVE, Role.TEACHER);
  }

  @Test
  void adminCannotDisableOwnAccount() {
    CurrentUserPrincipal admin = new CurrentUserPrincipal(1L, "admin", Role.ADMIN);

    assertThatThrownBy(() -> service.setStatus(admin, 1L, AdminUserStatus.DISABLED))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            exception -> {
              BusinessException businessException = (BusinessException) exception;
              assertThat(businessException.status()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(businessException.code()).isEqualTo("ADMIN_SELF_DISABLE_FORBIDDEN");
            });

    verifyNoInteractions(repository);
  }

  @Test
  void adminCanChangeAnotherUsersStatusAndRepositoryRevokesSessions() {
    CurrentUserPrincipal admin = new CurrentUserPrincipal(1L, "admin", Role.ADMIN);
    AdminUserDto disabled = new AdminUserDto(8L, "student", "Student", Role.STUDENT, "DISABLED");
    when(repository.setStatusAndRevokeSessions(eq(8L), eq(AdminUserStatus.DISABLED)))
        .thenReturn(java.util.Optional.of(disabled));

    assertThat(service.setStatus(admin, 8L, AdminUserStatus.DISABLED)).isEqualTo(disabled);
    verify(repository).setStatusAndRevokeSessions(8L, AdminUserStatus.DISABLED);
  }
}
