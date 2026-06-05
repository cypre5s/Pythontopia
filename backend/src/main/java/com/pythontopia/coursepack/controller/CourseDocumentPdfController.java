package com.pythontopia.coursepack.controller;

import jakarta.validation.constraints.Positive;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 把 7 章原始课件 PDF 暴露给 pdfjs-dist。
 *
 * <p>PDF 不存进 Git（单文件最大 ~80MB），由本地 {@code data/course-pack-43/pdfs/{docId}.pdf}
 * 提供。配置项 {@code pythontopia.coursepack.pdfDir} 控制根目录，可被环境变量
 * {@code PYT_COURSEPACK_PDF_DIR} 覆盖；缺失 PDF 时返回 404，让前端降级到 Markdown 视图。
 *
 * <p>故意不绑库，避免 documents 表无相对路径字段（仅 originalFilename / canonicalPath）时
 * 后端逻辑被字符串拼接搞乱。
 */
@Validated
@RestController
@RequestMapping("/api/course/documents")
public class CourseDocumentPdfController {

  private static final Logger LOG = LoggerFactory.getLogger(CourseDocumentPdfController.class);

  /** 仅允许 7 份课程包 43 文档；防止任意 path traversal / 越权枚举其它文件。 */
  private static final long MIN_DOC_ID = 186L;

  private static final long MAX_DOC_ID = 192L;

  private final Path pdfDir;

  public CourseDocumentPdfController(
      @Value("${pythontopia.coursepack.pdfDir:./data/course-pack-43/pdfs}") String configuredDir) {
    this.pdfDir = Paths.get(configuredDir).toAbsolutePath().normalize();
  }

  @GetMapping(value = "/{docId}/pdf")
  public ResponseEntity<Resource> streamPdf(@PathVariable @Positive Long docId) {
    if (docId < MIN_DOC_ID || docId > MAX_DOC_ID) {
      LOG.debug("course document {} out of pack 43 range", docId);
      return ResponseEntity.notFound().build();
    }
    Path candidate = pdfDir.resolve(docId + ".pdf").normalize();
    if (!candidate.startsWith(pdfDir)) {
      LOG.warn("rejected suspicious PDF path traversal target {}", candidate);
      return ResponseEntity.notFound().build();
    }
    if (!Files.isRegularFile(candidate)) {
      LOG.warn(
          "course document {} PDF missing at {} (configure pythontopia.coursepack.pdfDir)",
          docId,
          candidate);
      return ResponseEntity.notFound().build();
    }
    long size;
    try {
      size = Files.size(candidate);
    } catch (IOException ex) {
      LOG.error("failed to stat PDF {}", candidate, ex);
      return ResponseEntity.internalServerError().build();
    }
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_PDF);
    headers.setContentLength(size);
    headers.setContentDispositionFormData("inline", docId + ".pdf");
    headers.setCacheControl("private, max-age=86400");
    return ResponseEntity.ok().headers(headers).body(new FileSystemResource(candidate));
  }
}
