package com.pythontopia.ai.runtime;

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

class AiRuntimeSummaryControllerTest {

  private final AiRuntimeSummaryService service = mock(AiRuntimeSummaryService.class);
  private final AiRuntimeSummaryController controller = new AiRuntimeSummaryController(service);

  @Test
  void rejectsNonStudentWithForbidden() {
    CurrentUserPrincipal teacher = new CurrentUserPrincipal(2L, "teacher", Role.TEACHER);

    assertThatThrownBy(() -> controller.summary(teacher))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).status())
        .isEqualTo(HttpStatus.FORBIDDEN);

    verifyNoInteractions(service);
  }

  @Test
  void studentGetsOwnRuntimeSummary() {
    CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "stu", Role.STUDENT);
    AiRuntimeSummaryDto expected =
        new AiRuntimeSummaryDto(
            new AiRuntimeSummaryDto.ProtocolSummary(
                6,
                2,
                2,
                14,
                4,
                4,
                List.of("learner.recommendations.read"),
                List.of("learner_twin_memory")),
            new AiRuntimeSummaryDto.EvidenceSummary(4, 5, 1, 2, 0.7, 0.8, 0.3, 1, 0, 1),
            new AiRuntimeSummaryDto.LearnerTwinSummary(9, 3, 4, 1, 1, 1),
            new AiRuntimeSummaryDto.ExternalResearchSummary(
                List.of("docs.python.org"), 6, "外部内容，非课件"),
            List.of());
    when(service.summaryForUser(eq(7L))).thenReturn(expected);

    AiRuntimeSummaryDto result = controller.summary(student);

    assertThat(result).isSameAs(expected);
    verify(service).summaryForUser(eq(7L));
  }

  @Test
  void teacherGetsRuntimeOperationsForTheStudentTheyExplicitlyName() {
    CurrentUserPrincipal teacher = new CurrentUserPrincipal(2L, "teacher", Role.TEACHER);
    AiRuntimeOperationsDto.OperationsSubject subject =
        AiRuntimeOperationsDto.OperationsSubject.student(31L);
    AiRuntimeOperationsDto expected =
        new AiRuntimeOperationsDto(
            subject,
            List.of(
                new AiRuntimeOperationsDto.LoopOperation(
                    "RESOURCE",
                    "资源生成闭环",
                    "PASS",
                    "trace-resource",
                    "evaluate:resource_feedback_or_check",
                    List.of("resource_feedback_or_check"))),
            List.of(
                new AiRuntimeOperationsDto.GovernanceItem(
                    "policy_canary_review",
                    "策略灰度审核",
                    "PASS",
                    "查看 canary dry-run 与 rollback 状态",
                    "/teacher/review",
                    List.of("agentic-m3-system-evolution-v1"))));
    when(service.operationsForSubject(eq(subject))).thenReturn(expected);

    AiRuntimeOperationsDto result = controller.operations(teacher, 31L);

    assertThat(result).isSameAs(expected);
    assertThat(result.subject().userId()).isEqualTo(31L);
    verify(service).operationsForSubject(eq(subject));
  }

  /**
   * 教师本人没有学习事件，用查看者自己的 userId 建证据图只会恒得 INSUFFICIENT_EVIDENCE，
   * 与平台真实状态无关——必须 fail fast 要求指定数据主体，而不是静默拿教师自己的数据充数。
   */
  @Test
  void teacherWithoutExplicitSubjectIsRejectedInsteadOfSilentlyReadingOwnEvidence() {
    CurrentUserPrincipal teacher = new CurrentUserPrincipal(2L, "teacher", Role.TEACHER);

    assertThatThrownBy(() -> controller.operations(teacher, null))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> {
              assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(exception.code()).isEqualTo("AI_RUNTIME_SUBJECT_REQUIRED");
            });

    verifyNoInteractions(service);
  }

  @Test
  void studentOperationsAreScopedToSelfAndLabelledAsSuch() {
    CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "stu", Role.STUDENT);
    AiRuntimeOperationsDto.OperationsSubject subject =
        AiRuntimeOperationsDto.OperationsSubject.self(7L);
    AiRuntimeOperationsDto expected = new AiRuntimeOperationsDto(subject, List.of(), List.of());
    when(service.operationsForSubject(eq(subject))).thenReturn(expected);

    AiRuntimeOperationsDto result = controller.operations(student, null);

    assertThat(result.subject().scope()).isEqualTo("SELF");
    verify(service).operationsForSubject(eq(subject));
  }

  @Test
  void studentCannotAskForAnotherUsersRuntimeOperations() {
    CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "stu", Role.STUDENT);

    assertThatThrownBy(() -> controller.operations(student, 8L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN));

    verifyNoInteractions(service);
  }
}
