package com.pythontopia.coursepack.controller;

import com.pythontopia.coursepack.domain.CoursePage;
import com.pythontopia.coursepack.repository.CoursePackRepository;
import jakarta.validation.constraints.Positive;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
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
 * 课件证据「页面图片」：把命中的课件页（page_id）渲染成 PNG，供答疑气泡直接 {@code <img>} 内嵌。
 *
 * <p>服务端用 PDFBox 把原版 {@code data/course-pack-43/pdfs/{docId}.pdf} 的对应页渲染成 PNG，
 * 首次渲染后落盘缓存（{@code pythontopia.coursepack.pageImageDir}），后续请求直接回缓存文件——
 * 避免前端为一页证据下载整份 PDF（最大 ~80MB）。随 {@code /api/course/**} 匿名放行，
 * 因为 {@code <img src>} 不会带 Authorization 头。
 *
 * <p>page_id → (document_id, page_no) 由 {@link CoursePackRepository#findPageById} 解析；
 * 仅允许课程包 43 的 7 份文档（186~192），并做 path-traversal 防护。
 */
@Validated
@RestController
@RequestMapping("/api/course/pages")
public class CoursePageImageController {

  private static final Logger LOG = LoggerFactory.getLogger(CoursePageImageController.class);

  /** 仅允许课程包 43 的 7 份文档；与 CourseDocumentPdfController 一致，防越权枚举其它文件。 */
  private static final long MIN_DOC_ID = 186L;

  private static final long MAX_DOC_ID = 192L;

  /** 课件多为 PPT 式幻灯页，120 DPI 已足够清晰且单页 PNG 体积可控。 */
  private static final float RENDER_DPI = 120f;

  private final CoursePackRepository repository;
  private final Path pdfDir;
  private final Path cacheDir;
  private final ConcurrentHashMap<Long, Object> renderLocks = new ConcurrentHashMap<>();

  public CoursePageImageController(
      CoursePackRepository repository,
      @Value("${pythontopia.coursepack.pdfDir:./data/course-pack-43/pdfs}") String configuredPdfDir,
      @Value("${pythontopia.coursepack.pageImageDir:./data/course-pack-43/page-images}")
          String configuredCacheDir) {
    this.repository = repository;
    this.pdfDir = Paths.get(configuredPdfDir).toAbsolutePath().normalize();
    this.cacheDir = Paths.get(configuredCacheDir).toAbsolutePath().normalize();
  }

  @GetMapping(value = "/{pageId}/image", produces = MediaType.IMAGE_PNG_VALUE)
  public ResponseEntity<Resource> pageImage(@PathVariable @Positive Long pageId) {
    Optional<CoursePage> found = repository.findPageById(pageId);
    if (found.isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    CoursePage page = found.get();
    if (page.documentId() == null || page.pageNo() == null) {
      return ResponseEntity.notFound().build();
    }
    long docId = page.documentId();
    int pageNo = page.pageNo();
    if (docId < MIN_DOC_ID || docId > MAX_DOC_ID || pageNo < 1) {
      LOG.debug("page {} -> doc {} p{} out of range", pageId, docId, pageNo);
      return ResponseEntity.notFound().build();
    }
    Path pdf = pdfDir.resolve(docId + ".pdf").normalize();
    if (!pdf.startsWith(pdfDir) || !Files.isRegularFile(pdf)) {
      LOG.warn("page {} -> doc {} PDF missing at {}", pageId, docId, pdf);
      return ResponseEntity.notFound().build();
    }

    Path png;
    try {
      png = ensureRendered(pageId, pdf, pageNo);
    } catch (IOException ex) {
      LOG.error("failed to render page {} (doc {} p{})", pageId, docId, pageNo, ex);
      return ResponseEntity.internalServerError().build();
    }
    if (png == null) {
      return ResponseEntity.notFound().build();
    }

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.IMAGE_PNG);
    headers.setCacheControl("public, max-age=86400");
    return ResponseEntity.ok().headers(headers).body(new FileSystemResource(png));
  }

  /** 返回缓存 PNG 路径；不存在则按需渲染并落盘（同一 page 串行渲染，避免并发重复写）。 */
  private Path ensureRendered(long pageId, Path pdf, int pageNo) throws IOException {
    Path target = cacheDir.resolve(pageId + ".png").normalize();
    if (!target.startsWith(cacheDir)) {
      return null;
    }
    if (Files.isRegularFile(target) && Files.size(target) > 0) {
      return target;
    }
    Object lock = renderLocks.computeIfAbsent(pageId, key -> new Object());
    synchronized (lock) {
      if (Files.isRegularFile(target) && Files.size(target) > 0) {
        return target;
      }
      Files.createDirectories(cacheDir);
      try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
        if (pageNo > doc.getNumberOfPages()) {
          LOG.warn("page {} pageNo {} exceeds doc page count {}", pageId, pageNo, doc.getNumberOfPages());
          return null;
        }
        PDFRenderer renderer = new PDFRenderer(doc);
        BufferedImage image = renderer.renderImageWithDPI(pageNo - 1, RENDER_DPI, ImageType.RGB);
        Path tmp = Files.createTempFile(cacheDir, pageId + "-", ".png.tmp");
        try {
          if (!ImageIO.write(image, "png", tmp.toFile())) {
            throw new IOException("no PNG ImageWriter available");
          }
          Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
          Files.deleteIfExists(tmp);
        }
      }
    }
    return target;
  }
}
