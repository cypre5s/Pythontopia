package com.pythontopia.workshop.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pythontopia.common.error.BusinessException;
import com.pythontopia.workshop.upload.dto.UploadDetailDto;
import com.pythontopia.workshop.upload.dto.UploadDto;
import com.pythontopia.workshop.upload.entity.UploadStatus;
import com.pythontopia.workshop.upload.entity.WorkshopUpload;
import com.pythontopia.workshop.upload.parser.UploadTextExtractor;
import com.pythontopia.workshop.upload.repository.UploadRepository;
import com.pythontopia.workshop.upload.service.UploadService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

/** 上传服务单测：真实解析器 + mock 仓储。覆盖白名单(415)/大小(413)/空文件、解析成功与 fail-soft、越权隔离。 */
class UploadServiceTest {

  private static final long USER_ID = 7L;

  private UploadRepository repository;
  private UploadService service;

  @BeforeEach
  void setUp() {
    repository = mock(UploadRepository.class);
    service = new UploadService(repository, new UploadTextExtractor());
    when(repository.hasUploadCapacity(anyLong(), anyLong(), anyInt(), anyLong())).thenReturn(true);
    when(repository.canAcceptUpload(anyLong(), anyLong(), anyInt(), anyLong())).thenReturn(true);
    // 仓储 insert 回显入参为持久化行（id/createdAt 由库生成），便于断言返回 DTO 的解析结果。
    when(repository.insert(
            anyLong(), anyString(), anyString(), anyLong(), any(), anyInt(), anyInt(), any(), any()))
        .thenAnswer(
            invocation ->
                new WorkshopUpload(
                    100L,
                    invocation.getArgument(0),
                    invocation.getArgument(1),
                    invocation.getArgument(2),
                    invocation.getArgument(3),
                    invocation.getArgument(4),
                    invocation.getArgument(5),
                    invocation.getArgument(6),
                    invocation.getArgument(7),
                    invocation.getArgument(8),
                    OffsetDateTime.now()));
  }

  @Test
  void uploadTextFileExtractsAndMarksReady() {
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "main.py", "text/x-python", "print('hi')\nx = 1\n".getBytes(StandardCharsets.UTF_8));

    UploadDto dto = service.upload(USER_ID, file);

    assertThat(dto.status()).isEqualTo(UploadStatus.READY);
    assertThat(dto.ext()).isEqualTo(".py");
    assertThat(dto.filename()).isEqualTo("main.py");
    assertThat(dto.charCount()).isEqualTo(18);
    assertThat(dto.lineCount()).isEqualTo(2);
    assertThat(dto.byteSize()).isEqualTo(18L);
    assertThat(dto.error()).isNull();
  }

  @Test
  void uploadStripsClientPathFromFilename() {
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "C:\\Users\\stu\\notes.md", "text/markdown", "# title".getBytes(StandardCharsets.UTF_8));

    UploadDto dto = service.upload(USER_ID, file);

    assertThat(dto.filename()).isEqualTo("notes.md");
    assertThat(dto.ext()).isEqualTo(".md");
  }

  @Test
  void uploadPersistsExtractedPdfText() throws IOException {
    byte[] pdf = samplePdf("Hello PDF world");
    MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", pdf);

    UploadDto dto = service.upload(USER_ID, file);

    assertThat(dto.status()).isEqualTo(UploadStatus.READY);
    ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
    verify(repository)
        .insert(
            anyLong(),
            anyString(),
            eq(".pdf"),
            anyLong(),
            textCaptor.capture(),
            anyInt(),
            anyInt(),
            eq(UploadStatus.READY),
            any());
    assertThat(textCaptor.getValue()).contains("Hello PDF world");
  }

  @Test
  void uploadRejectsUnsupportedExtensionWith415() {
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "evil.exe", "application/octet-stream", "x".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> service.upload(USER_ID, file))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).status())
        .isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    verify(repository, never())
        .insert(anyLong(), anyString(), anyString(), anyLong(), any(), anyInt(), anyInt(), any(), any());
  }

  @Test
  void uploadRejectsOversizeWith413() {
    byte[] big = new byte[(int) (5L * 1024 * 1024) + 1];
    MockMultipartFile file = new MockMultipartFile("file", "big.txt", "text/plain", big);

    assertThatThrownBy(() -> service.upload(USER_ID, file))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).status())
        .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
  }

  @Test
  void uploadRejectsWhenPerUserStorageQuotaIsExceeded() {
    when(repository.canAcceptUpload(USER_ID, 1L, 20, 50L * 1024 * 1024)).thenReturn(false);
    MockMultipartFile file =
        new MockMultipartFile("file", "note.txt", "text/plain", "x".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> service.upload(USER_ID, file))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            exception -> {
              BusinessException businessException = (BusinessException) exception;
              assertThat(businessException.status()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
              assertThat(businessException.code()).isEqualTo("UPLOAD_QUOTA_EXCEEDED");
            });

    verify(repository, never())
        .insert(anyLong(), anyString(), anyString(), anyLong(), any(), anyInt(), anyInt(), any(), any());
  }

  @Test
  void checksStorageQuotaBeforeParsingTheFile() {
    when(repository.hasUploadCapacity(USER_ID, 1L, 20, 50L * 1024 * 1024)).thenReturn(false);
    UploadTextExtractor extractor = spy(new UploadTextExtractor());
    UploadService quotaService = new UploadService(repository, extractor);
    MockMultipartFile file =
        new MockMultipartFile("file", "note.txt", "text/plain", "x".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> quotaService.upload(USER_ID, file))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).code())
        .isEqualTo("UPLOAD_QUOTA_EXCEEDED");

    verify(extractor, never()).extract(eq(".txt"), any(byte[].class));
  }

  @Test
  void uploadRejectsEmptyFile() {
    MockMultipartFile file = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

    assertThatThrownBy(() -> service.upload(USER_ID, file))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).status())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void uploadRejectsMissingFilename() {
    MockMultipartFile file =
        new MockMultipartFile("file", null, "text/plain", "x".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> service.upload(USER_ID, file))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).status())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void uploadFailSoftMarksFailedWithoutThrowingOnCorruptBinary() {
    byte[] garbage = "not a real pdf".getBytes(StandardCharsets.UTF_8);
    MockMultipartFile file = new MockMultipartFile("file", "broken.pdf", "application/pdf", garbage);

    UploadDto dto = service.upload(USER_ID, file);

    assertThat(dto.status()).isEqualTo(UploadStatus.FAILED);
    assertThat(dto.error()).isNotBlank();
    assertThat(dto.charCount()).isZero();
    assertThat(dto.lineCount()).isZero();
  }

  @Test
  void listMapsRowsToDtos() {
    when(repository.listByUser(USER_ID)).thenReturn(List.of(readyUpload()));

    List<UploadDto> result = service.list(USER_ID);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).status()).isEqualTo(UploadStatus.READY);
  }

  @Test
  void detailReturnsExtractedTextForOwner() {
    when(repository.findByIdForUser(USER_ID, 100L)).thenReturn(Optional.of(readyUpload()));

    UploadDetailDto detail = service.detail(USER_ID, 100L);

    assertThat(detail.extractedText()).isEqualTo("hello world");
    verify(repository).findByIdForUser(USER_ID, 100L);
  }

  @Test
  void detailNotFoundThrows404() {
    when(repository.findByIdForUser(USER_ID, 404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.detail(USER_ID, 404L))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).status())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void deleteMissingRowThrows404() {
    when(repository.deleteByIdForUser(USER_ID, 404L)).thenReturn(0);

    assertThatThrownBy(() -> service.delete(USER_ID, 404L))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).status())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void deleteOwnedRowSucceeds() {
    when(repository.deleteByIdForUser(USER_ID, 100L)).thenReturn(1);

    service.delete(USER_ID, 100L);

    verify(repository).deleteByIdForUser(USER_ID, 100L);
  }

  @Test
  void extractedTextReturnsTextOnlyForReadyUpload() {
    when(repository.findByIdForUser(USER_ID, 100L)).thenReturn(Optional.of(readyUpload()));
    when(repository.findByIdForUser(USER_ID, 200L)).thenReturn(Optional.of(failedUpload()));

    assertThat(service.extractedText(USER_ID, 100L)).contains("hello world");
    assertThat(service.extractedText(USER_ID, 200L)).isEmpty();
  }

  @Test
  void nullFileRejected() {
    assertThatThrownBy(() -> service.upload(USER_ID, null))
        .isInstanceOf(BusinessException.class);
    verifyNoInteractions(repository);
  }

  private static WorkshopUpload readyUpload() {
    return new WorkshopUpload(
        100L, USER_ID, "main.py", ".py", 11L, "hello world", 11, 1, UploadStatus.READY, null,
        OffsetDateTime.now());
  }

  private static WorkshopUpload failedUpload() {
    return new WorkshopUpload(
        200L, USER_ID, "broken.pdf", ".pdf", 9L, "", 0, 0, UploadStatus.FAILED, "PDF 解析失败：x",
        OffsetDateTime.now());
  }

  private static byte[] samplePdf(String text) throws IOException {
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage();
      document.addPage(page);
      try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
        stream.beginText();
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        stream.newLineAtOffset(72, 700);
        stream.showText(text);
        stream.endText();
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      document.save(out);
      return out.toByteArray();
    }
  }
}
