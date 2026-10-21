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

class AdminCourseResourceServiceTest {

  private final AdminCourseResourceRepository repository = mock(AdminCourseResourceRepository.class);
  private final AdminCourseResourceService service = new AdminCourseResourceService(repository);

  @Test
  void studentsCannotBrowseAdminCourseCatalog() {
    CurrentUserPrincipal student = new CurrentUserPrincipal(2L, "student", Role.STUDENT);

    assertThatThrownBy(() -> service.page(student, 0, 50, null, AdminCourseResourceType.ALL))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            exception -> assertThat(((BusinessException) exception).status()).isEqualTo(HttpStatus.FORBIDDEN));

    verifyNoInteractions(repository);
  }

  @Test
  void adminSearchesRealCourseResourcesWithPagination() {
    CurrentUserPrincipal admin = new CurrentUserPrincipal(1L, "admin", Role.ADMIN);
    AdminCourseResourcePageDto expected = new AdminCourseResourcePageDto(List.of(), 0, 50, 0, 0);
    when(repository.page(0, 50, "循环", AdminCourseResourceType.ALL)).thenReturn(expected);

    assertThat(service.page(admin, 0, 50, " 循环 ", AdminCourseResourceType.ALL)).isSameAs(expected);
    verify(repository).page(eq(0), eq(50), eq("循环"), eq(AdminCourseResourceType.ALL));
  }

  @Test
  void adminCanReversiblyChangeResourceAvailability() {
    CurrentUserPrincipal admin = new CurrentUserPrincipal(1L, "admin", Role.ADMIN);

    service.setEnabled(admin, AdminCourseResourceType.EXAMPLE, 12L, false);

    verify(repository).setEnabled(AdminCourseResourceType.EXAMPLE, 12L, false, 1L);
  }

  @Test
  void studentsCannotChangeCourseResourceAvailability() {
    CurrentUserPrincipal student = new CurrentUserPrincipal(2L, "student", Role.STUDENT);

    assertThatThrownBy(() -> service.setEnabled(student, AdminCourseResourceType.EXAMPLE, 12L, false))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            exception -> assertThat(((BusinessException) exception).status()).isEqualTo(HttpStatus.FORBIDDEN));

    verifyNoInteractions(repository);
  }
}
