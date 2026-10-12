package com.pythontopia.tutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.tutor.dto.TutorWorkflowAttachmentDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

class TutorWorkflowControllerTest {

  private final TutorWorkflowService service = mock(TutorWorkflowService.class);
  private final TutorWorkflowController controller = new TutorWorkflowController(service);
  private static final CurrentUserPrincipal STUDENT =
      new CurrentUserPrincipal(7L, "student", Role.STUDENT);
  private static final CurrentUserPrincipal TEACHER =
      new CurrentUserPrincipal(2L, "teacher", Role.TEACHER);

  @Test
  void uploadDelegatesWithAuthenticatedOwnerAndSessionKey() {
    var file = new MockMultipartFile("file", "image.png", "image/png", new byte[] {1});
    var expected = new TutorWorkflowAttachmentDto("d0162eb8-8c60-4d85-a3da-3aa3f11460ba");
    when(service.uploadAttachment(7L, "wf-7", file)).thenReturn(expected);

    var result = controller.uploadAttachment(STUDENT, "wf-7", file);

    assertThat(result).isEqualTo(expected);
    verify(service).uploadAttachment(7L, "wf-7", file);
  }

  @Test
  void uploadRejectsNonStudents() {
    var file = new MockMultipartFile("file", "image.png", "image/png", new byte[] {1});

    assertThatThrownBy(() -> controller.uploadAttachment(TEACHER, "wf-7", file))
        .isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).status())
        .isEqualTo(HttpStatus.FORBIDDEN);
    verifyNoInteractions(service);
  }
}
