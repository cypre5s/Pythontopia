package com.pythontopia.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AdminUsageServiceTest {

  private final AdminUsageRepository repository = Mockito.mock(AdminUsageRepository.class);
  private final AdminUsageService service = new AdminUsageService(repository);

  @Test
  void onlyAdminCanReadUsage() {
    assertThatThrownBy(
            () -> service.list(new CurrentUserPrincipal(3L, "teacher", Role.TEACHER), 30, null))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void returnsActualRowsAndDoesNotInventUnknownTokenCounts() {
    var row = new AdminUsageDto.Event(1L, "LOOP_RUN", 5L, "student", "学习闭环", Instant.EPOCH,
        "完成", 12, 4);
    var expected = new AdminUsageDto(List.of(row), 30, 1, 12L, 4L, 0);
    when(repository.list(30, "student")).thenReturn(expected);

    var result = service.list(new CurrentUserPrincipal(1L, "admin", Role.ADMIN), 30, " student ");

    assertThat(result).isEqualTo(expected);
    verify(repository).list(30, "student");
  }
}
