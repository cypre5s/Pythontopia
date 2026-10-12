package com.pythontopia.workshop.upload.parser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

/**
 * 把上传文件字节解析为纯文本。文本类（.py/.txt/.md/.csv）按 UTF-8 直读；{@code .pdf} 用 Apache PDFBox；
 * {@code .docx} 用 Apache POI。输入文件大小由 service 限制；本类另限制 PDF 页数、DOCX 解压总量、DOCX
 * 条目数与最终文本长度。解析失败统一抛 {@link UploadParseException}（service 落 status=FAILED，不抛 500）。
 */
@Component
public class UploadTextExtractor {

  /** 允许的扩展名（小写含点）。service 据此对非白名单返回 415；此处再兜底防御。 */
  public static final Set<String> ALLOWED_EXTENSIONS =
      Set.of(".py", ".txt", ".md", ".csv", ".pdf", ".docx");

  private static final Set<String> TEXT_EXTENSIONS = Set.of(".py", ".txt", ".md", ".csv");
  private static final int MAX_PDF_PAGES = 100;
  private static final int MAX_DOCX_ENTRIES = 100;
  private static final long MAX_DOCX_EXPANDED_BYTES = 16L * 1024 * 1024;

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
    try (PDDocument document =
        Loader.loadPDF(bytes, "", null, null, IOUtils.createTempFileOnlyStreamCache())) {
      if (document.getNumberOfPages() > MAX_PDF_PAGES) {
        throw new UploadParseException("PDF 页数超过上限（100 页）");
      }
      PDFTextStripper stripper = new PDFTextStripper();
      stripper.setEndPage(MAX_PDF_PAGES);
      BoundedTextWriter text = new BoundedTextWriter();
      stripper.writeText(document, text);
      return text.toString();
    } catch (UploadParseException e) {
      throw e;
    } catch (TextLimitExceeded e) {
      throw new UploadParseException("提取文本超过上限（100000 字符）");
    } catch (IOException | RuntimeException e) {
      throw new UploadParseException("PDF 解析失败：" + safeMessage(e));
    }
  }

  private String extractDocx(byte[] bytes) {
    validateDocxArchive(bytes);
    // 关闭 XWPFDocument 即释放底层资源；XWPFWordExtractor 不持有额外句柄，无需重复 close。
    try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
      return new XWPFWordExtractor(document).getText();
    } catch (IOException | RuntimeException e) {
      throw new UploadParseException("DOCX 解析失败：" + safeMessage(e));
    }
  }

  private void validateDocxArchive(byte[] bytes) {
    long expandedBytes = 0;
    int entries = 0;
    byte[] buffer = new byte[8192];
    try (ZipInputStream archive = new ZipInputStream(new ByteArrayInputStream(bytes))) {
      ZipEntry entry;
      while ((entry = archive.getNextEntry()) != null) {
        if (++entries > MAX_DOCX_ENTRIES) {
          throw new UploadParseException("DOCX 文件条目超过上限（100 个）");
        }
        int read;
        while ((read = archive.read(buffer)) != -1) {
          expandedBytes += read;
          if (expandedBytes > MAX_DOCX_EXPANDED_BYTES) {
            throw new UploadParseException("DOCX 解压后内容超过上限（16 MiB）");
          }
        }
      }
    } catch (UploadParseException e) {
      throw e;
    } catch (IOException e) {
      throw new UploadParseException("DOCX 解析失败：" + safeMessage(e));
    }
  }

  private static final class BoundedTextWriter extends Writer {

    private final StringBuilder text = new StringBuilder();

    @Override
    public void write(char[] chars, int offset, int length) throws IOException {
      if (text.length() + length > ExtractedContent.MAX_CHAR_COUNT) {
        throw new TextLimitExceeded();
      }
      text.append(chars, offset, length);
    }

    @Override
    public void write(String value, int offset, int length) throws IOException {
      if (text.length() + length > ExtractedContent.MAX_CHAR_COUNT) {
        throw new TextLimitExceeded();
      }
      text.append(value, offset, offset + length);
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}

    @Override
    public String toString() {
      return text.toString();
    }
  }

  private static final class TextLimitExceeded extends IOException {}

  private static String safeMessage(Throwable e) {
    String message = e.getMessage();
    return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
  }
}
