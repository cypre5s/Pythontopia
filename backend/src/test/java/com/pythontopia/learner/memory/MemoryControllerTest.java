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
import com.pythontopia.learner.memory.dto.MemoryItemDto;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** 记忆笔记本只读 API 单测：仅学生可用、只取本人数据、limit 透传。 */
class MemoryControllerTest {

  private final MemoryRetrievalService service = mock(MemoryRetrievalService.class);
  private final MemoryController controller = new MemoryController(service);

  private static MemoryItemDto sample() {
    return new MemoryItemDto(
        1L, "practice_failed", "练习失败：PY43-KC-016（WA）", List.of("PY43-KC-016"),
        "2026-06-08T00:00:00Z", 4, 1.0, 0.8, 1.0, 0.9);
  }

  @Test
  void rejectsNonStudentWithForbidden() {
    CurrentUserPrincipal teacher = new CurrentUserPrincipal(2L, "teacher", Role.TEACHER);

    assertThatThrownBy(() -> controller.memory(teacher, null, 8))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).status())
        .isEqualTo(HttpStatus.FORBIDDEN);

    verifyNoInteractions(service);
  }

  @Test
  void studentGetsOwnMemoryWithFocusAndLimit() {
    CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "stu", Role.STUDENT);
    when(service.retrieve(eq(7L), eq("PY43-KC-016"), eq(5))).thenReturn(List.of(sample()));

    List<MemoryItemDto> result = controller.memory(student, "PY43-KC-016", 5);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).eventId()).isEqualTo(1L);
    // 必须用当前登录者 userId 检索，杜绝越权读他人记忆。
    verify(service).retrieve(eq(7L), eq("PY43-KC-016"), eq(5));
  }
}
