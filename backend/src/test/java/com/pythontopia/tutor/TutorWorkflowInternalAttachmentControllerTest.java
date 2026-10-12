package com.pythontopia.tutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class TutorWorkflowInternalAttachmentControllerTest {

  private final TutorWorkflowAttachmentService service = mock(TutorWorkflowAttachmentService.class);
  private final TutorWorkflowInternalAttachmentController controller =
      new TutorWorkflowInternalAttachmentController(
          service, new GraphServiceProperties("http://graph", "http://rag", "internal-test-token"));

  @Test
  void validInternalSubjectReturnsPrivateBytesWithoutCaching() {
    byte[] png = {(byte) 0x89, 'P', 'N', 'G'};
    when(service.readForGraph(7, "session-a", "attachment-a"))
        .thenReturn(new TutorWorkflowStoredImage("image/png", png));

    var response = controller.read("session-a", "attachment-a", "internal-test-token", "user:7");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType().toString()).isEqualTo("image/png");
    assertThat(response.getHeaders().getCacheControl()).contains("no-store");
    assertThat(response.getBody()).containsExactly(png);
    verify(service).readForGraph(7, "session-a", "attachment-a");
  }

  @Test
  void rejectsInvalidTokenAndSubjectBeforeReadingAnyAttachment() {
    assertThatThrownBy(() -> controller.read("session-a", "attachment-a", "wrong", "user:7"))
        .isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).status())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThatThrownBy(
            () -> controller.read("session-a", "attachment-a", "internal-test-token", "user:0"))
        .isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).status())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    verify(service, never()).readForGraph(7, "session-a", "attachment-a");
  }
}
