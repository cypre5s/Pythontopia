package com.pythontopia.learner.twin;

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
import com.pythontopia.learner.twin.dto.WeeklyReportDto;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** 数字孪生周报只读 API 单测：仅学生可用、只取本人数据。 */
class TwinWeeklyControllerTest {

  private final WeeklyReportService service = mock(WeeklyReportService.class);
  private final TwinWeeklyController controller = new TwinWeeklyController(service);

  private static WeeklyReportDto sample() {
    return new WeeklyReportDto(
        "2026-06-02",
        "2026-06-09",
        new WeeklyReportDto.MemoryBrief(3, 1, 1, List.of("PY43-KC-001"), List.of("x")),
        List.of(),
        List.of(new WeeklyReportDto.MasteryDeltaBrief("PY43-KC-001", 0.6, 0.4, List.of())),
        List.of(new WeeklyReportDto.ResourceRunBrief("handout", "讲义", "SUCCESS", 2, "2026-06-08T00:00:00Z")));
  }

  @Test
  void rejectsNonStudentWithForbidden() {
    CurrentUserPrincipal teacher = new CurrentUserPrincipal(2L, "teacher", Role.TEACHER);

    assertThatThrownBy(() -> controller.weekly(teacher))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).status())
        .isEqualTo(HttpStatus.FORBIDDEN);

    verifyNoInteractions(service);
  }

  @Test
  void studentGetsOwnWeeklyReport() {
    CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "stu", Role.STUDENT);
    when(service.build(eq(7L))).thenReturn(sample());

    WeeklyReportDto result = controller.weekly(student);

    assertThat(result.periodStart()).isEqualTo("2026-06-02");
    assertThat(result.resourceRuns().get(0).status()).isEqualTo("SUCCESS");
    // 必须用当前登录者 userId 聚合，杜绝越权读他人周报。
    verify(service).build(eq(7L));
  }
}
