package com.pythontopia.workshop.upload.parser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

/**
 * 把上传文件字节解析为纯文本。文本类（.py/.txt/.md/.csv）按 UTF-8 直读；{@code .pdf} 用 Apache PDFBox；
 * {@code .docx} 用 Apache POI。扩展名白名单与大小限制在 service 层校验，本类只处理已知白名单扩展名，
 * 解析失败统一抛 {@link UploadParseException}（service 落 status=FAILED，不抛 500）。
 */
@Component
public class UploadTextExtractor {

  /** 允许的扩展名（小写含点）。service 据此对非白名单返回 415；此处再兜底防御。 */
  public static final Set<String> ALLOWED_EXTENSIONS =
      Set.of(".py", ".txt", ".md", ".csv", ".pdf", ".docx");

  private static final Set<String> TEXT_EXTENSIONS = Set.of(".py", ".txt", ".md", ".csv");

  public UploadIngestionBoundary boundary() {
    return new UploadIngestionBoundary(
        "local_text_pdf_docx",
        Set.copyOf(ALLOWED_EXTENSIONS),
        false,
        false,
        Map.of(
            ".py", "utf8_text",
            ".txt", "utf8_text",
            ".md", "utf8_text",
            ".csv", "utf8_text",
            ".pdf", "pdfbox_text",
            ".docx", "poi_xwpf_text"));
  }

  public ExtractedContent extract(String ext, byte[] bytes) {
    if (bytes == null) {
      throw new UploadParseException("文件内容为空");
    }
    if (TEXT_EXTENSIONS.contains(ext)) {
      return ExtractedContent.of(new String(bytes, StandardCharsets.UTF_8));
    }
    if (".pdf".equals(ext)) {
      return ExtractedContent.of(extractPdf(bytes));
    }
    if (".docx".equals(ext)) {
      return ExtractedContent.of(extractDocx(bytes));
    }
    throw new UploadParseException("不支持的文件类型：" + ext);
  }

  private String extractPdf(byte[] bytes) {
    try (PDDocument document = Loader.loadPDF(bytes)) {
      return new PDFTextStripper().getText(document);
    } catch (IOException | RuntimeException e) {
      throw new UploadParseException("PDF 解析失败：" + safeMessage(e));
    }
  }

  private String extractDocx(byte[] bytes) {
    // 关闭 XWPFDocument 即释放底层资源；XWPFWordExtractor 不持有额外句柄，无需重复 close。
    try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
      return new XWPFWordExtractor(document).getText();
    } catch (IOException | RuntimeException e) {
      throw new UploadParseException("DOCX 解析失败：" + safeMessage(e));
    }
  }

  private static String safeMessage(Throwable e) {
    String message = e.getMessage();
    return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
  }
}
