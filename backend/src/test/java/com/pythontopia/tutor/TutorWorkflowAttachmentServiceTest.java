package com.pythontopia.tutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pythontopia.common.error.BusinessException;
import com.pythontopia.tutor.dto.TutorWorkflowSessionDto;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class TutorWorkflowAttachmentServiceTest {

  private static final long USER_ID = 7L;
  private static final long SESSION_ID = 41L;
  private static final String SESSION_KEY = "wf-private-image";
  private static final TutorWorkflowSessionDto SESSION =
      new TutorWorkflowSessionDto(SESSION_ID, SESSION_KEY, 9L, "READING", 0, "ACTIVE");
  private final TutorWorkflowRepository sessions = mock(TutorWorkflowRepository.class);
  private final TutorWorkflowAttachmentRepository attachments =
      mock(TutorWorkflowAttachmentRepository.class);
  private final TutorWorkflowAttachmentService service =
      new TutorWorkflowAttachmentService(sessions, attachments);

  @BeforeEach
  void setUp() {
    when(sessions.findBySessionKey(USER_ID, SESSION_KEY)).thenReturn(Optional.of(SESSION));
    when(attachments.activeUsage(USER_ID, SESSION_ID))
        .thenReturn(new TutorWorkflowAttachmentUsage(0, 0, 0, 0));
  }

  @Test
  void uploadBindsDetectedImageBytesToTheOwnedWorkflowSession() {
    byte[] png = pngBytes();

    var result =
        service.upload(
            USER_ID, SESSION_KEY, new MockMultipartFile("file", "question.png", "image/png", png));

    assertThat(UUID.fromString(result.attachmentId())).isNotNull();
    verify(attachments)
        .insert(eq(SESSION_ID), eq(UUID.fromString(result.attachmentId())), eq("image/png"), eq(png), any(Instant.class));
  }

  @Test
  void uploadRejectsMismatchedOrUnsupportedImageContent() {
    var fakePng = new MockMultipartFile("file", "question.png", "image/png", "not png".getBytes());

    assertThatThrownBy(() -> service.upload(USER_ID, SESSION_KEY, fakePng))
        .isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).status())
        .isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    verify(attachments, never()).insert(any(Long.class), any(), any(), any(), any());
  }

  @Test
  void uploadRejectsOversizedImage() {
    byte[] oversized = new byte[TutorWorkflowAttachmentService.MAX_IMAGE_BYTES + 1];
    var file = new MockMultipartFile("file", "large.png", "image/png", oversized);

    assertThatThrownBy(() -> service.upload(USER_ID, SESSION_KEY, file))
        .isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).status())
        .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    verify(attachments, never()).insert(any(Long.class), any(), any(), any(), any());
  }

  @Test
  void uploadRejectsOversizedFileBeforeReadingItsBytes() throws Exception {
    MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
    when(file.isEmpty()).thenReturn(false);
    when(file.getSize()).thenReturn((long) TutorWorkflowAttachmentService.MAX_IMAGE_BYTES + 1);
    when(file.getContentType()).thenReturn("image/png");

    assertThatThrownBy(() -> service.upload(USER_ID, SESSION_KEY, file))
        .isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).status())
        .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    verify(file, never()).getBytes();
  }

  @Test
  void readForGraphOnlyReturnsBytesForTheMatchingOwnerAndSession() {
    UUID id = UUID.fromString("d0162eb8-8c60-4d85-a3da-3aa3f11460ba");
    byte[] png = pngBytes();
    when(attachments.findActiveForOwnerSession(USER_ID, SESSION_ID, id))
        .thenReturn(Optional.of(new TutorWorkflowStoredImage("image/png", png)));

    TutorWorkflowStoredImage result = service.readForGraph(USER_ID, SESSION_KEY, id.toString());

    assertThat(result.contentType()).isEqualTo("image/png");
    assertThat(result.content()).containsExactly(png);
    verify(attachments).findActiveForOwnerSession(USER_ID, SESSION_ID, id);
  }

  @Test
  void readForGraphHidesExpiredForeignOrDeletedImagesAsNotFound() {
    UUID id = UUID.fromString("d0162eb8-8c60-4d85-a3da-3aa3f11460ba");
    when(attachments.findActiveForOwnerSession(USER_ID, SESSION_ID, id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.readForGraph(USER_ID, SESSION_KEY, id.toString()))
        .isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).status())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void uploadEnforcesSessionAndUserCountQuota() {
    var file = new MockMultipartFile("file", "question.png", "image/png", pngBytes());
    when(attachments.activeUsage(USER_ID, SESSION_ID))
        .thenReturn(new TutorWorkflowAttachmentUsage(10, 0, 10, 0));

    assertThatThrownBy(() -> service.upload(USER_ID, SESSION_KEY, file))
        .isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).status())
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    verify(attachments, never()).insert(any(Long.class), any(), any(), any(), any());
  }

  @Test
  void uploadEnforcesSessionAggregateByteQuotaBeforeReadingBytes() throws Exception {
    MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
    when(file.isEmpty()).thenReturn(false);
    when(file.getSize()).thenReturn((long) pngBytes().length);
    when(file.getContentType()).thenReturn("image/png");
    when(attachments.activeUsage(USER_ID, SESSION_ID))
        .thenReturn(new TutorWorkflowAttachmentUsage(0, 25L * 1024 * 1024 - 1, 0, 0));

    assertThatThrownBy(() -> service.upload(USER_ID, SESSION_KEY, file))
        .isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).status())
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    verify(file, never()).getBytes();
  }

  @Test
  void uploadEnforcesUserAttachmentCountQuota() {
    var file = new MockMultipartFile("file", "question.png", "image/png", pngBytes());
    when(attachments.activeUsage(USER_ID, SESSION_ID))
        .thenReturn(new TutorWorkflowAttachmentUsage(0, 0, 40, 0));

    assertThatThrownBy(() -> service.upload(USER_ID, SESSION_KEY, file))
        .isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).status())
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    verify(attachments, never()).insert(any(Long.class), any(), any(), any(), any());
  }

  @Test
  void uploadEnforcesUserAggregateByteQuota() {
    var file = new MockMultipartFile("file", "question.png", "image/png", pngBytes());
    when(attachments.activeUsage(USER_ID, SESSION_ID))
        .thenReturn(new TutorWorkflowAttachmentUsage(0, 0, 39, 100L * 1024 * 1024 - 1));

    assertThatThrownBy(() -> service.upload(USER_ID, SESSION_KEY, file))
        .isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).status())
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    verify(attachments, never()).insert(any(Long.class), any(), any(), any(), any());
  }

  @Test
  void uploadRequiresAnActiveSessionOwnedByTheCaller() {
    when(sessions.findBySessionKey(8L, SESSION_KEY)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.upload(
                    8L,
                    SESSION_KEY,
                    new MockMultipartFile("file", "question.png", "image/png", pngBytes())))
        .isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).status())
        .isEqualTo(HttpStatus.NOT_FOUND);
    verify(attachments, never()).insert(any(Long.class), any(), any(), any(), any());
  }

  @Test
  void cleanupRemovesExpiredAttachmentRows() {
    when(attachments.deleteExpired()).thenReturn(1);

    service.cleanupExpired();

    verify(attachments).deleteExpired();
  }

  private static byte[] pngBytes() {
    return new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0};
  }
}
