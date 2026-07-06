package com.pythontopia.learner.reflection;

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
import com.pythontopia.learner.reflection.dto.LearnerInsightDto;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** 学情洞察只读 API 单测：仅学生、只读本人、limit clamp、倒序透传。 */
class InsightControllerTest {

  private final LearnerInsightRepository repository = mock(LearnerInsightRepository.class);
  private final InsightController controller = new InsightController(repository);

  private static LearnerInsightDto insight(long id, String text) {
    return new LearnerInsightDto(
        id, text, "error_pattern", List.of("mutable_alias"), List.of(10L, 11L), 0.8, "GRAPH",
        "2026-06-08T00:00:00Z");
  }

  @Test
  void rejectsNonStudentWithForbidden() {
    CurrentUserPrincipal teacher = new CurrentUserPrincipal(2L, "teacher", Role.TEACHER);

    assertThatThrownBy(() -> controller.insights(teacher, 10))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).status())
        .isEqualTo(HttpStatus.FORBIDDEN);

    verifyNoInteractions(repository);
  }

  @Test
  void studentGetsOwnInsightsWithEvidence() {
    CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "stu", Role.STUDENT);
    when(repository.findByUser(eq(7L), eq(10)))
        .thenReturn(List.of(insight(2, "新洞察"), insight(1, "旧洞察")));

    List<LearnerInsightDto> result = controller.insights(student, 10);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).id()).isEqualTo(2);
    assertThat(result.get(0).evidenceEventIds()).containsExactly(10L, 11L);
    verify(repository).findByUser(eq(7L), eq(10));
  }

  @Test
  void clampsLimitToOneToFifty() {
    CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "stu", Role.STUDENT);
    when(repository.findByUser(eq(7L), eq(50))).thenReturn(List.of());
    when(repository.findByUser(eq(7L), eq(1))).thenReturn(List.of());

    controller.insights(student, 999);
    controller.insights(student, 0);

    verify(repository).findByUser(eq(7L), eq(50));
    verify(repository).findByUser(eq(7L), eq(1));
  }
}
