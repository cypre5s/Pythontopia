package com.pythontopia.workshop.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.workshop.upload.controller.UploadController;
import com.pythontopia.workshop.upload.dto.UploadDetailDto;
import com.pythontopia.workshop.upload.dto.UploadDto;
import com.pythontopia.workshop.upload.entity.UploadStatus;
import com.pythontopia.workshop.upload.retrieval.UploadCorpusIndexingService;
import com.pythontopia.workshop.upload.service.UploadService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

/** 上传控制器单测：仅 STUDENT 可用，且始终以当前登录者 userId 调服务（杜绝越权）。 */
class UploadControllerTest {

  private final UploadService service = mock(UploadService.class);
  private final UploadCorpusIndexingService indexingService =
      mock(UploadCorpusIndexingService.class);
  private final StudentWriteThrottle studentWriteThrottle = mock(StudentWriteThrottle.class);
  private final UploadController controller =
      new UploadController(service, indexingService, studentWriteThrottle);

  private static final CurrentUserPrincipal STUDENT =
      new CurrentUserPrincipal(7L, "stu", Role.STUDENT);
  private static final CurrentUserPrincipal TEACHER =
      new CurrentUserPrincipal(2L, "teacher", Role.TEACHER);

  private static MockMultipartFile sampleFile() {
    return new MockMultipartFile(
        "file", "main.py", "text/x-python", "print('hi')".getBytes(StandardCharsets.UTF_8));
  }

  private static UploadDto sampleDto() {
    return new UploadDto(1L, "main.py", ".py", 11L, 11, 1, UploadStatus.READY, null);
  }

  @Test
  void uploadRejectsNonStudentWithForbidden() {
    assertThatThrownBy(() -> controller.upload(TEACHER, sampleFile()))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).status())
        .isEqualTo(HttpStatus.FORBIDDEN);
    verifyNoInteractions(service);
  }

  @Test
  void uploadDelegatesToServiceWithCurrentUser() {
    MockMultipartFile file = sampleFile();
    when(studentWriteThrottle.tryAcquire(7L, 1)).thenReturn(true);
    when(service.upload(eq(7L), eq(file))).thenReturn(sampleDto());

    UploadDto dto = controller.upload(STUDENT, file);

    assertThat(dto.id()).isEqualTo(1L);
    verify(service).upload(7L, file);
    // READY 上传应触发 B 通路异步分块+向量化。
    verify(indexingService).indexUploadAsync(1L);
  }

  @Test
  void uploadStillSucceedsWhenIndexingQueueIsFull() {
    MockMultipartFile file = sampleFile();
    when(studentWriteThrottle.tryAcquire(7L, 1)).thenReturn(true);
    when(service.upload(eq(7L), eq(file))).thenReturn(sampleDto());
    doThrow(new TaskRejectedException("queue full"))
        .when(indexingService)
        .indexUploadAsync(1L);

    UploadDto dto = controller.upload(STUDENT, file);

    assertThat(dto.id()).isEqualTo(1L);
    verify(service).upload(7L, file);
    verify(indexingService).indexUploadAsync(1L);
  }

  @Test
  void uploadRejectsWhenStudentWriteBudgetIsExhausted() {
    when(studentWriteThrottle.tryAcquire(7L, 1)).thenReturn(false);

    assertThatThrownBy(() -> controller.upload(STUDENT, sampleFile()))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).status())
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

    verify(studentWriteThrottle).tryAcquire(7L, 1);
    verifyNoInteractions(service, indexingService);
  }

  @Test
  void listDelegatesToServiceWithCurrentUser() {
    when(service.list(7L)).thenReturn(List.of(sampleDto()));

    List<UploadDto> result = controller.list(STUDENT);

    assertThat(result).hasSize(1);
    verify(service).list(7L);
  }

  @Test
  void listRejectsNonStudent() {
    assertThatThrownBy(() -> controller.list(TEACHER))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).status())
        .isEqualTo(HttpStatus.FORBIDDEN);
    verifyNoInteractions(service);
  }

  @Test
  void detailDelegatesToServiceWithCurrentUser() {
    UploadDetailDto detail =
        new UploadDetailDto(5L, "main.py", ".py", 11L, 11, 1, UploadStatus.READY, null, "print('hi')");
    when(service.detail(7L, 5L)).thenReturn(detail);

    UploadDetailDto result = controller.detail(STUDENT, 5L);

    assertThat(result.extractedText()).isEqualTo("print('hi')");
    verify(service).detail(7L, 5L);
  }

  @Test
  void detailRejectsNonStudent() {
    assertThatThrownBy(() -> controller.detail(TEACHER, 5L))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).status())
        .isEqualTo(HttpStatus.FORBIDDEN);
    verifyNoInteractions(service);
  }

  @Test
  void deleteDelegatesToServiceWithCurrentUser() {
    when(studentWriteThrottle.tryAcquire(7L, 1)).thenReturn(true);

    controller.delete(STUDENT, 5L);

    verify(service).delete(7L, 5L);
  }

  @Test
  void deleteRejectsWhenStudentWriteBudgetIsExhausted() {
    when(studentWriteThrottle.tryAcquire(7L, 1)).thenReturn(false);

    assertThatThrownBy(() -> controller.delete(STUDENT, 5L))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).status())
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

    verify(studentWriteThrottle).tryAcquire(7L, 1);
    verifyNoInteractions(service, indexingService);
  }

  @Test
  void deleteRejectsNonStudent() {
    assertThatThrownBy(() -> controller.delete(TEACHER, 5L))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).status())
        .isEqualTo(HttpStatus.FORBIDDEN);
    verifyNoInteractions(service);
  }
}
