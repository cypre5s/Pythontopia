package com.pythontopia.learner.memory;

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
import com.pythontopia.learner.memory.dto.MemoryBriefDto;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** 记忆 brief API 单测（测试矩阵 ⑥「非 student 403」）：仅学生可用，且只取登录态 userId（杜绝越权）。 */
class MemoryBriefControllerTest {

  private final MemoryBriefService service = mock(MemoryBriefService.class);
  private final MemoryBriefController controller = new MemoryBriefController(service);

  @Test
  void rejectsNonStudentWithForbidden() {
    CurrentUserPrincipal teacher = new CurrentUserPrincipal(2L, "teacher", Role.TEACHER);

    assertThatThrownBy(() -> controller.brief(teacher, "列表"))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).status())
        .isEqualTo(HttpStatus.FORBIDDEN);

    verifyNoInteractions(service);
  }

  @Test
  void studentGetsBriefForOwnUserId() {
    CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "stu", Role.STUDENT);
    MemoryBriefDto expected =
        new MemoryBriefDto(
            List.of("练习失败：PY43-KC-07"),
            List.of("PY43-KC-07"),
            List.of("认知风格: visual"),
            "2026-06-17T05:52:00Z");
    when(service.buildBrief(eq(7L), eq("列表"))).thenReturn(expected);

    MemoryBriefDto result = controller.brief(student, "列表");

    assertThat(result).isSameAs(expected);
    // 必须用登录者 userId 构建，杜绝越权读他人记忆。
    verify(service).buildBrief(eq(7L), eq("列表"));
  }
}
