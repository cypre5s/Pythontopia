package com.pythontopia.workshop.upload.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.workshop.upload.retrieval.dto.RetrievalHit;
import com.pythontopia.workshop.upload.retrieval.dto.RetrieveRequest;
import com.pythontopia.workshop.upload.retrieval.dto.RetrieveResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** {@link UploadRetrievalController} 单测：仅学生可用、检索强制用登录态 userId scoped、参数透传。 */
class UploadRetrievalControllerTest {

  private final UploadRetrievalService service = mock(UploadRetrievalService.class);
  private final UploadRetrievalController controller =
      new UploadRetrievalController(service, new StudentWriteThrottle());

  @Test
  void rejectsNonStudentWithForbidden() {
    CurrentUserPrincipal teacher = new CurrentUserPrincipal(2L, "teacher", Role.TEACHER);

    assertThatThrownBy(
            () -> controller.retrieve(teacher, new RetrieveRequest("列表", null, null)))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).status())
        .isEqualTo(HttpStatus.FORBIDDEN);

    verifyNoInteractions(service);
  }

  @Test
  void studentRetrievesOwnCorpusScopedByPrincipalUserId() {
    CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "stu", Role.STUDENT);
    RetrievalHit hit = new RetrievalHit(2L, "main.py", 0, "for 循环示例", 0.91);
    when(service.retrieve(eq(7L), eq("for循环"), eq(List.of(2L)), eq(3)))
        .thenReturn(List.of(hit));

    RetrieveResponse response =
        controller.retrieve(student, new RetrieveRequest("for循环", List.of(2L), 3));

    assertThat(response.hits()).containsExactly(hit);
    // 必须以登录者 userId 检索，杜绝越权读他人语料。
    verify(service).retrieve(eq(7L), eq("for循环"), eq(List.of(2L)), eq(3));
  }

  @Test
  void rateLimitsRepeatedExpensiveRetrievalsPerStudent() {
    CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "stu", Role.STUDENT);
    RetrieveRequest request = new RetrieveRequest("for循环", null, null);
    when(service.retrieve(eq(7L), eq("for循环"), eq(null), eq(null))).thenReturn(List.of());

    for (int requestIndex = 0; requestIndex < 30; requestIndex++) {
      controller.retrieve(student, request);
    }

    assertThatThrownBy(() -> controller.retrieve(student, request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> {
              assertThat(ex.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
              assertThat(ex.code()).isEqualTo("RETRIEVAL_RATE_LIMITED");
            });

    verify(service, times(30)).retrieve(eq(7L), eq("for循环"), eq(null), eq(null));
  }
}
