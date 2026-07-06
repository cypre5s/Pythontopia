package com.pythontopia.workshop.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pythontopia.workshop.upload.parser.ExtractedContent;
import com.pythontopia.workshop.upload.parser.UploadParseException;
import com.pythontopia.workshop.upload.parser.UploadTextExtractor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

/** 解析器单测：文本/PDF/DOCX 抽取与计数，以及损坏文件 fail-fast 抛 {@link UploadParseException}。 */
class UploadTextExtractorTest {

  private final UploadTextExtractor extractor = new UploadTextExtractor();

  @Test
  void extractsPlainTextWithCharAndLineCounts() {
    byte[] bytes = "print('hi')\nx = 1\n".getBytes(StandardCharsets.UTF_8);

    ExtractedContent content = extractor.extract(".py", bytes);

    assertThat(content.text()).isEqualTo("print('hi')\nx = 1\n");
    assertThat(content.charCount()).isEqualTo(18);
    assertThat(content.lineCount()).isEqualTo(2);
  }

  @Test
  void normalizesCrlfBeforeCounting() {
    byte[] bytes = "a,b\r\nc,d".getBytes(StandardCharsets.UTF_8);

    ExtractedContent content = extractor.extract(".csv", bytes);

    assertThat(content.text()).isEqualTo("a,b\nc,d");
    assertThat(content.lineCount()).isEqualTo(2);
  }

  @Test
  void extractsTextFromPdf() throws IOException {
    byte[] pdf = samplePdf("Hello PDF world");

    ExtractedContent content = extractor.extract(".pdf", pdf);

    assertThat(content.text()).contains("Hello PDF world");
    assertThat(content.charCount()).isGreaterThan(0);
  }

  @Test
  void extractsTextFromDocx() throws IOException {
    byte[] docx = sampleDocx("Hello DOCX world");

    ExtractedContent content = extractor.extract(".docx", docx);

    assertThat(content.text()).contains("Hello DOCX world");
  }

  @Test
  void throwsParseExceptionOnCorruptPdf() {
    byte[] garbage = "this is definitely not a pdf".getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(() -> extractor.extract(".pdf", garbage))
        .isInstanceOf(UploadParseException.class)
        .hasMessageContaining("PDF 解析失败");
  }

  @Test
  void throwsParseExceptionOnCorruptDocx() {
    byte[] garbage = "this is definitely not a docx".getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(() -> extractor.extract(".docx", garbage))
        .isInstanceOf(UploadParseException.class)
        .hasMessageContaining("DOCX 解析失败");
  }

  @Test
  void boundaryDeclaresLocalTextPdfDocxAdapterWithoutExternalOcrOrMarker() {
    var boundary = extractor.boundary();

    assertThat(boundary.adapterName()).isEqualTo("local_text_pdf_docx");
    assertThat(boundary.supportedExtensions()).contains(".py", ".txt", ".md", ".csv", ".pdf", ".docx");
    assertThat(boundary.externalOcrEnabled()).isFalse();
    assertThat(boundary.markerEnabled()).isFalse();
    assertThat(boundary.extractionModes())
        .containsEntry(".pdf", "pdfbox_text")
        .containsEntry(".docx", "poi_xwpf_text")
        .containsEntry(".py", "utf8_text");
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

  private static byte[] sampleDocx(String text) throws IOException {
    try (XWPFDocument document = new XWPFDocument()) {
      document.createParagraph().createRun().setText(text);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      document.write(out);
      return out.toByteArray();
    }
  }
}
