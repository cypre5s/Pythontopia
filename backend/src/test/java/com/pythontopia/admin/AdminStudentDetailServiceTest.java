package com.pythontopia.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.learner.profile.repository.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AdminStudentDetailServiceTest {

  private final AdminStudentDetailRepository repository = mock(AdminStudentDetailRepository.class);
  private final ProfileRepository profileRepository = mock(ProfileRepository.class);
  private final AdminStudentDetailService service =
      new AdminStudentDetailService(repository, profileRepository, new ObjectMapper());

  @Test
  void nonAdminCannotReadStudentDetails() {
    CurrentUserPrincipal student = new CurrentUserPrincipal(2L, "student", Role.STUDENT);

    assertThatThrownBy(() -> service.get(student, 7L))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            exception ->
                assertThat(((BusinessException) exception).status()).isEqualTo(HttpStatus.FORBIDDEN));

    verifyNoInteractions(repository, profileRepository);
  }

  @Test
  void unknownStudentReturnsNotFoundWithoutReadingActivity() {
    CurrentUserPrincipal admin = new CurrentUserPrincipal(1L, "admin", Role.ADMIN);

    assertThatThrownBy(() -> service.get(admin, 999L))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            exception ->
                assertThat(((BusinessException) exception).status()).isEqualTo(HttpStatus.NOT_FOUND));
  }
}
